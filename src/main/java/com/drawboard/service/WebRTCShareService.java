package com.drawboard.service;

import com.drawboard.domain.Page;
import com.drawboard.domain.elements.CanvasElement;
import com.drawboard.domain.elements.ImageElement;
import com.drawboard.webrtc.PageShareSession;
import com.drawboard.webrtc.PageUpdateEvent;
import com.drawboard.webrtc.ShareOffer;
import dev.onvoid.webrtc.*;
import io.avaje.jsonb.Jsonb;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Service for sharing pages via WebRTC.
 * Manages peer connections, data channels, and real-time page updates.
 */
@Singleton
public class WebRTCShareService {
    private static final Logger log = LoggerFactory.getLogger(WebRTCShareService.class);
    private static final String DEFAULT_VIEWER_URL = "https://petermucha.github.io/drawboard/";
    private static final long UPDATE_DEBOUNCE_MS = 500;

    private final PageService pageService;
    private final Jsonb jsonb;
    private final Map<String, PageShareSession> activeSessions = new ConcurrentHashMap<>();  // pageId -> session
    private final Map<String, String> sessionIdToPageId = new ConcurrentHashMap<>();  // sessionId -> pageId
    private final Map<String, ScheduledFuture<?>> pendingUpdates = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private PeerConnectionFactory factory;

    public WebRTCShareService(PageService pageService, Jsonb jsonb) {
        this.pageService = pageService;
        this.jsonb = jsonb;
    }

    @PostConstruct
    public void initialize() {
        // Initialize WebRTC native library
        try {
            factory = new PeerConnectionFactory();
            log.info("WebRTC peer connection factory initialized");
        } catch (Exception e) {
            log.error("Failed to initialize WebRTC factory", e);
            throw new RuntimeException("Failed to initialize WebRTC", e);
        }

        // Subscribe to page updates
        pageService.addUpdateListener(event -> {
            PageShareSession session = activeSessions.get(event.pageId());
            if (session != null && session.isConnected()) {
                schedulePageUpdate(session, event.page());
            }
        });
        log.info("WebRTC share service initialized");
    }

    @PreDestroy
    public void shutdown() {
        // Stop all active sessions
        activeSessions.values().forEach(this::closeSession);
        activeSessions.clear();
        pendingUpdates.values().forEach(future -> future.cancel(false));
        pendingUpdates.clear();
        scheduler.shutdown();
        if (factory != null) {
            factory.dispose();
        }
        log.info("WebRTC share service shutdown");
    }

    /**
     * Create a WebRTC share offer for a page.
     *
     * @param notebookId The notebook ID
     * @param chapterId The chapter ID
     * @param pageId The page ID
     * @return ShareOffer containing session ID, SDP, and share URL
     */
    public ShareOffer createShareOffer(String notebookId, String chapterId, String pageId) {
        try {
            String sessionId = UUID.randomUUID().toString();

            // Configure WebRTC with STUN server
            RTCConfiguration config = new RTCConfiguration();
            RTCIceServer stunServer = new RTCIceServer();
            stunServer.urls.add("stun:stun.l.google.com:19302");
            config.iceServers.add(stunServer);

            // Latch to wait for ICE gathering completion
            CountDownLatch iceGatheringLatch = new CountDownLatch(1);

            // Create peer connection
            RTCPeerConnection peerConnection = factory.createPeerConnection(config, new PeerConnectionObserver() {
                @Override
                public void onIceCandidate(RTCIceCandidate candidate) {
                    if (candidate != null && candidate.sdp != null) {
                        log.debug("ICE candidate generated: {} ({})", candidate.sdp, candidate.sdpMid);
                    } else {
                        log.info("ICE gathering complete (null candidate received)");
                        iceGatheringLatch.countDown();
                    }
                }

                @Override
                public void onConnectionChange(RTCPeerConnectionState state) {
                    log.info("Peer connection state changed: {}", state);
                    String pid = sessionIdToPageId.get(sessionId);
                    if (pid != null) {
                        PageShareSession session = activeSessions.get(pid);
                        if (session != null) {
                            if (state == RTCPeerConnectionState.CONNECTED) {
                                session.setConnected(true);
                                log.info("Peer connection CONNECTED - waiting for data channel to open");
                            } else if (state == RTCPeerConnectionState.FAILED) {
                                log.error("Peer connection FAILED");
                                session.setConnected(false);
                            }
                        }
                    }
                }

                @Override
                public void onDataChannel(RTCDataChannel dataChannel) {
                    log.info("Data channel received: {}", dataChannel.getLabel());
                }
            });

            // Create data channel
            RTCDataChannelInit channelInit = new RTCDataChannelInit();
            channelInit.ordered = true;
            RTCDataChannel dataChannel = peerConnection.createDataChannel("page-data", channelInit);

            // Set up data channel callbacks
            dataChannel.registerObserver(new RTCDataChannelObserver() {
                @Override
                public void onBufferedAmountChange(long previousAmount) {
                    // Not needed
                }

                @Override
                public void onStateChange() {
                    RTCDataChannelState state = dataChannel.getState();
                    log.info("Data channel state changed to: {}", state);

                    if (state == RTCDataChannelState.OPEN) {
                        log.info("Data channel OPEN - sending initial page data");
                        String pid = sessionIdToPageId.get(sessionId);
                        if (pid != null) {
                            PageShareSession session = activeSessions.get(pid);
                            if (session != null) {
                                session.setConnected(true);
                                sendInitialPageData(session);
                            } else {
                                log.error("Session not found for pageId: {}", pid);
                            }
                        } else {
                            log.error("PageId not found for sessionId: {}", sessionId);
                        }
                    } else if (state == RTCDataChannelState.CLOSED) {
                        log.warn("Data channel CLOSED");
                    } else if (state == RTCDataChannelState.CLOSING) {
                        log.warn("Data channel CLOSING");
                    }
                }

                @Override
                public void onMessage(RTCDataChannelBuffer buffer) {
                    // Viewer is read-only, ignore incoming messages
                    log.debug("Received message from viewer (ignoring)");
                }
            });

            // Create session
            PageShareSession session = new PageShareSession(sessionId, notebookId, chapterId, pageId, peerConnection);
            session.setDataChannel(dataChannel);
            activeSessions.put(pageId, session);
            sessionIdToPageId.put(sessionId, pageId);
            log.debug("Created session mapping: sessionId={} -> pageId={}", sessionId, pageId);

            // Create offer
            RTCOfferOptions offerOptions = new RTCOfferOptions();
            CountDownLatch latch = new CountDownLatch(1);
            RTCSessionDescription[] offerHolder = new RTCSessionDescription[1];

            peerConnection.createOffer(offerOptions, new CreateSessionDescriptionObserver() {
                @Override
                public void onSuccess(RTCSessionDescription description) {
                    peerConnection.setLocalDescription(description, new SetSessionDescriptionObserver() {
                        @Override
                        public void onSuccess() {
                            log.info("Local description set - ICE gathering will start");
                            offerHolder[0] = description;
                            latch.countDown();
                        }

                        @Override
                        public void onFailure(String error) {
                            log.error("Failed to set local description: {}", error);
                            latch.countDown();
                        }
                    });
                }

                @Override
                public void onFailure(String error) {
                    log.error("Failed to create offer: {}", error);
                    latch.countDown();
                }
            });

            // Wait for offer creation (with timeout)
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Timeout creating WebRTC offer");
            }

            if (offerHolder[0] == null) {
                throw new RuntimeException("Failed to create WebRTC offer");
            }

            log.info("Waiting for ICE gathering to complete (this may take 3 seconds)...");

            // Wait for ICE gathering to complete (with longer timeout)
            if (!iceGatheringLatch.await(3, TimeUnit.SECONDS)) {
                log.warn("Timeout waiting for ICE gathering after 3 seconds - proceeding anyway");
            } else {
                log.info("ICE gathering completed successfully");
            }

            // Get the updated local description which should now include ICE candidates
            RTCSessionDescription updatedDescription = peerConnection.getLocalDescription();
            if (updatedDescription != null && updatedDescription.sdp != null) {
                int candidateCount = countIceCandidates(updatedDescription.sdp);
                log.info("Offer includes {} ICE candidate(s)", candidateCount);

                if (candidateCount > 0) {
                    offerHolder[0] = updatedDescription;
                    log.info("Using updated offer with ICE candidates");
                } else {
                    log.warn("No ICE candidates found in updated description - using original");
                }
            } else {
                log.warn("Could not get updated local description - using original offer");
            }

            String offerSdp = offerHolder[0].sdp;
            if (offerSdp == null || offerSdp.trim().isEmpty()) {
                throw new RuntimeException("Offer SDP is null or empty");
            }

            log.debug("Generated offer SDP (first 100 chars): {}", offerSdp.substring(0, Math.min(100, offerSdp.length())));

            String shareUrl = ShareOffer.generateShareUrl(DEFAULT_VIEWER_URL, offerSdp);

            log.info("Created share offer for page {} (session: {}), URL length: {} chars",
                pageId, sessionId, shareUrl.length());

            return new ShareOffer(sessionId, offerSdp, shareUrl);

        } catch (Exception e) {
            log.error("Failed to create share offer", e);
            throw new RuntimeException("Failed to create share offer", e);
        }
    }

    /**
     * Process answer SDP from the viewer.
     *
     * @param pageId The page ID
     * @param answerSdp The answer SDP string
     */
    public void processAnswer(String pageId, String answerSdp) {
        PageShareSession session = activeSessions.get(pageId);
        if (session == null) {
            log.warn("No active session found for page: {}", pageId);
            throw new IllegalArgumentException("No active session for page: " + pageId);
        }

        try {
            log.debug("Received answer SDP with {} lines", answerSdp.split("\n").length);

            // Normalize line endings to CRLF (\r\n) as required by SDP spec
            String normalizedSdp = normalizeLineEndings(answerSdp);

            log.debug("Sanitized SDP has {} lines", normalizedSdp.split("\r\n").length);

            RTCSessionDescription answer = new RTCSessionDescription(RTCSdpType.ANSWER, normalizedSdp);
            CountDownLatch latch = new CountDownLatch(1);

            session.peerConnection().setRemoteDescription(answer, new SetSessionDescriptionObserver() {
                @Override
                public void onSuccess() {
                    log.info("Remote description set successfully for session {}", session.sessionId());
                    latch.countDown();
                }

                @Override
                public void onFailure(String error) {
                    log.error("Failed to set remote description: {}", error);
                    latch.countDown();
                }
            });

            // Wait for answer processing (with timeout)
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Timeout processing answer");
            }

        } catch (Exception e) {
            log.error("Failed to process answer", e);
            throw new RuntimeException("Failed to process answer", e);
        }
    }

    /**
     * Stop sharing a page.
     *
     * @param pageId The page ID
     */
    public void stopSharing(String pageId) {
        PageShareSession session = activeSessions.remove(pageId);
        if (session != null) {
            sessionIdToPageId.remove(session.sessionId());
            closeSession(session);
            log.info("Stopped sharing page: {}", pageId);
        }

        // Cancel any pending updates
        ScheduledFuture<?> pending = pendingUpdates.remove(pageId);
        if (pending != null) {
            pending.cancel(false);
        }
    }

    /**
     * Check if a page is currently being shared.
     *
     * @param pageId The page ID
     * @return true if the page is being shared
     */
    public boolean isSharing(String pageId) {
        PageShareSession session = activeSessions.get(pageId);
        return session != null && session.isConnected();
    }

    // ==================== Private Helper Methods ====================

    private void sendInitialPageData(PageShareSession session) {
        try {
            // Load page from storage
            Page page = pageService.getPage(session.notebookId(), session.chapterId(), session.pageId());
            if (page == null) {
                log.error("Page not found: {}", session.pageId());
                return;
            }

            // Send page data
            sendPageData(session, page);

            // Send all images
            for (CanvasElement element : page.elements()) {
                if (element instanceof ImageElement imageElement) {
                    sendImage(session, imageElement);
                }
            }

            log.info("Sent initial page data for page {}", session.pageId());

        } catch (Exception e) {
            log.error("Failed to send initial page data", e);
        }
    }

    private void sendPageData(PageShareSession session, Page page) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "page_data");
            message.put("page", page);

            String json = jsonb.toJson(message);
            sendMessage(session, json);

        } catch (Exception e) {
            log.error("Failed to send page data", e);
        }
    }

    private void sendImage(PageShareSession session, ImageElement imageElement) {
        try {
            byte[] imageData = pageService.getImageData(
                session.notebookId(),
                session.chapterId(),
                session.pageId(),
                imageElement.filename()
            );

            if (imageData == null) {
                log.warn("Image not found: {}", imageElement.filename());
                return;
            }

            // Determine MIME type from filename
            String mimeType = getMimeType(imageElement.filename());

            // Base64 encode image
            String base64Data = Base64.getEncoder().encodeToString(imageData);

            Map<String, Object> message = new HashMap<>();
            message.put("type", "image_data");
            message.put("filename", imageElement.filename());
            message.put("data", base64Data);
            message.put("mimeType", mimeType);

            String json = jsonb.toJson(message);
            sendMessage(session, json);

        } catch (Exception e) {
            log.error("Failed to send image: {}", imageElement.filename(), e);
        }
    }

    private void schedulePageUpdate(PageShareSession session, Page page) {
        String pageId = session.pageId();

        // Cancel any existing pending update
        ScheduledFuture<?> existing = pendingUpdates.get(pageId);
        if (existing != null) {
            existing.cancel(false);
        }

        // Schedule new update
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                Map<String, Object> message = new HashMap<>();
                message.put("type", "page_update");
                message.put("page", page);

                String json = jsonb.toJson(message);
                sendMessage(session, json);

                log.debug("Sent page update for page {}", pageId);
            } catch (Exception e) {
                log.error("Failed to send page update", e);
            } finally {
                pendingUpdates.remove(pageId);
            }
        }, UPDATE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);

        pendingUpdates.put(pageId, future);
    }

    private void sendMessage(PageShareSession session, String message) {
        if (session.dataChannel() == null || session.dataChannel().getState() != RTCDataChannelState.OPEN) {
            log.warn("Data channel not open, cannot send message");
            return;
        }

        try {
            byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            RTCDataChannelBuffer dcBuffer = new RTCDataChannelBuffer(buffer, false);
            session.dataChannel().send(dcBuffer);
        } catch (Exception e) {
            log.error("Failed to send message via data channel", e);
        }
    }

    private void closeSession(PageShareSession session) {
        try {
            if (session.dataChannel() != null) {
                session.dataChannel().close();
            }
            if (session.peerConnection() != null) {
                session.peerConnection().close();
            }
        } catch (Exception e) {
            log.error("Error closing session", e);
        }
    }

    private String getMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/png"; // default
    }

    /**
     * Count ICE candidates in SDP.
     */
    private int countIceCandidates(String sdp) {
        if (sdp == null) return 0;
        int count = 0;
        for (String line : sdp.split("\r?\n")) {
            if (line.trim().startsWith("a=candidate:")) {
                count++;
            }
        }
        return count;
    }

    /**
     * Normalize SDP line endings to CRLF (\r\n) as required by RFC 4566.
     * SDP spec requires lines to end with \r\n, but text areas may use just \n.
     *
     * @param sdp The SDP string with potentially mixed line endings
     * @return The SDP string with normalized CRLF line endings
     */
    private String normalizeLineEndings(String sdp) {
        if (sdp == null || sdp.isEmpty()) {
            return sdp;
        }

        // First, normalize all line endings to \n
        String normalized = sdp.replace("\r\n", "\n").replace("\r", "\n");

        // Then convert to \r\n
        normalized = normalized.replace("\n", "\r\n");

        // Ensure it ends with \r\n
        if (!normalized.endsWith("\r\n")) {
            normalized += "\r\n";
        }

        log.debug("Normalized line endings (original length: {}, normalized: {})",
            sdp.length(), normalized.length());

        return normalized;
    }

}
