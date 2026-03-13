package com.drawboard.service;

import com.drawboard.domain.Page;
import com.drawboard.domain.elements.CanvasElement;
import com.drawboard.domain.elements.ImageElement;
import com.drawboard.webrtc.PageShareSession;
import com.drawboard.webrtc.PageUpdateEvent;
import com.drawboard.webrtc.ShareSession;
import com.drawboard.webrtc.SignalingOffer;
import com.drawboard.webrtc.PeerJSWebSocketClient;
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
    private static final String DEFAULT_VIEWER_URL = "https://warmuuh.github.io/drawboard/";
    private static final long UPDATE_DEBOUNCE_MS = 500;

    private final PageService pageService;
    private final Jsonb jsonb;
    private final Map<String, PageShareSession> activeSessions = new ConcurrentHashMap<>();  // pageId -> session
    private final Map<String, String> sessionIdToPageId = new ConcurrentHashMap<>();  // sessionId -> pageId
    private final Map<String, ScheduledFuture<?>> pendingUpdates = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, ScheduledFuture<?>> periodicUpdateTasks = new ConcurrentHashMap<>();
    private final Map<String, String> lastPageHashes = new ConcurrentHashMap<>();  // pageId -> content hash
    private PeerConnectionFactory factory;
    private PeerJSWebSocketClient signalingClient;

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
        periodicUpdateTasks.values().forEach(future -> future.cancel(false));
        periodicUpdateTasks.clear();
        lastPageHashes.clear();
        scheduler.shutdown();

        if (signalingClient != null) {
            signalingClient.close();
        }

        if (factory != null) {
            factory.dispose();
        }
        log.info("WebRTC share service shutdown");
    }

    /**
     * Start sharing a page and return peer ID for URL generation.
     * The desktop app will wait for the viewer to connect.
     */
    public ShareSession startSharing(String notebookId, String chapterId, String pageId) {
        // Generate peer ID
        String peerId = UUID.randomUUID().toString();

        // Create session (without peer connection yet - that comes when offer arrives)
        PageShareSession session = new PageShareSession(peerId, notebookId, chapterId, pageId, null);
        activeSessions.put(pageId, session);
        sessionIdToPageId.put(peerId, pageId);

        // Connect to PeerJS server via WebSocket
        signalingClient = new PeerJSWebSocketClient(peerId, offer -> {
            handleIncomingOffer(pageId, offer);
        });

        try {
            if (!signalingClient.connectAndWait(10, TimeUnit.SECONDS)) {
                throw new RuntimeException("Failed to connect to PeerJS server");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Connection interrupted", e);
        }

        log.info("Started sharing page {} with peer ID {}", pageId, peerId);

        String shareUrl = ShareSession.generateShareUrl(DEFAULT_VIEWER_URL, peerId);
        return new ShareSession(peerId, shareUrl);
    }

    /**
     * Handle incoming offer from viewer and create answer.
     */
    private void handleIncomingOffer(String pageId, SignalingOffer offer) {
        log.info("Received offer from viewer {} for page {}", offer.fromPeerId(), pageId);

        PageShareSession session = activeSessions.get(pageId);
        if (session == null) {
            log.warn("No session found for page: {}", pageId);
            return;
        }

        try {
            // Configure peer connection
            RTCConfiguration config = new RTCConfiguration();
            RTCIceServer stunServer = new RTCIceServer();
            stunServer.urls.add("stun:stun.l.google.com:19302");
            config.iceServers.add(stunServer);

            CountDownLatch iceGatheringLatch = new CountDownLatch(1);

            // Create peer connection
            RTCPeerConnection peerConnection = factory.createPeerConnection(config, new PeerConnectionObserver() {
                @Override
                public void onIceCandidate(RTCIceCandidate candidate) {
                    if (candidate == null) {
                        iceGatheringLatch.countDown();
                    }
                }

                @Override
                public void onConnectionChange(RTCPeerConnectionState state) {
                    log.info("Connection state: {}", state);
                    if (state == RTCPeerConnectionState.CONNECTED) {
                        session.setConnected(true);
                    }
                }

                @Override
                public void onDataChannel(RTCDataChannel dataChannel) {
                    log.info("Data channel received: {}", dataChannel.getLabel());
                    session.setDataChannel(dataChannel);
                    setupDataChannel(session, dataChannel);
                }
            });

            // Update session with peer connection
            session.setPeerConnection(peerConnection);
            session.setViewerPeerId(offer.fromPeerId());

            // Set remote description (OFFER from viewer)
            RTCSessionDescription offerDesc = new RTCSessionDescription(RTCSdpType.OFFER, offer.sdp());
            CountDownLatch offerLatch = new CountDownLatch(1);

            peerConnection.setRemoteDescription(offerDesc, new SetSessionDescriptionObserver() {
                @Override
                public void onSuccess() {
                    log.info("Remote description (offer) set successfully");
                    offerLatch.countDown();
                }

                @Override
                public void onFailure(String error) {
                    log.error("Failed to set remote description: {}", error);
                    offerLatch.countDown();
                }
            });

            if (!offerLatch.await(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Timeout setting remote description");
            }

            // Create ANSWER
            RTCAnswerOptions answerOptions = new RTCAnswerOptions();
            CountDownLatch answerLatch = new CountDownLatch(1);
            RTCSessionDescription[] answerHolder = new RTCSessionDescription[1];

            peerConnection.createAnswer(answerOptions, new CreateSessionDescriptionObserver() {
                @Override
                public void onSuccess(RTCSessionDescription answer) {
                    peerConnection.setLocalDescription(answer, new SetSessionDescriptionObserver() {
                        @Override
                        public void onSuccess() {
                            log.info("Local description (answer) set - waiting for ICE gathering");
                            answerHolder[0] = answer;
                            answerLatch.countDown();
                        }

                        @Override
                        public void onFailure(String error) {
                            log.error("Failed to set local description: {}", error);
                            answerLatch.countDown();
                        }
                    });
                }

                @Override
                public void onFailure(String error) {
                    log.error("Failed to create answer: {}", error);
                    answerLatch.countDown();
                }
            });

            if (!answerLatch.await(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Timeout creating answer");
            }

            if (answerHolder[0] == null) {
                throw new RuntimeException("Failed to create answer");
            }

            // Wait for ICE gathering
            if (!iceGatheringLatch.await(3, TimeUnit.SECONDS)) {
                log.warn("ICE gathering timeout - proceeding anyway");
            }

            // Get answer with ICE candidates
            RTCSessionDescription finalAnswer = peerConnection.getLocalDescription();
            String answerSdp = finalAnswer != null ? finalAnswer.sdp : answerHolder[0].sdp;

            log.info("Sending answer to viewer {}", offer.fromPeerId());

            // Send answer back via signaling
            signalingClient.sendAnswer(offer.fromPeerId(), offer.connectionId(), answerSdp);

        } catch (Exception e) {
            log.error("Failed to handle incoming offer", e);
        }
    }

    private void setupDataChannel(PageShareSession session, RTCDataChannel dataChannel) {
        dataChannel.registerObserver(new RTCDataChannelObserver() {
            @Override
            public void onStateChange() {
                RTCDataChannelState state = dataChannel.getState();
                log.info("Data channel state: {}", state);

                if (state == RTCDataChannelState.OPEN) {
                    log.info("Data channel OPEN - sending initial page data");
                    session.setConnected(true);
                    sendInitialPageData(session);
                }
            }

            @Override
            public void onMessage(RTCDataChannelBuffer buffer) {
                // Viewer is read-only
            }

            @Override
            public void onBufferedAmountChange(long previousAmount) {}
        });
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
            stopPeriodicUpdates(pageId);
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

            // Start periodic updates to continuously stream changes
            startPeriodicUpdates(session);

        } catch (Exception e) {
            log.error("Failed to send initial page data", e);
        }
    }

    /**
     * Start periodic page updates for continuous streaming.
     * Checks for page changes every 500ms and sends updates only if content changed.
     */
    private void startPeriodicUpdates(PageShareSession session) {
        String pageId = session.pageId();

        // Cancel any existing periodic task
        ScheduledFuture<?> existing = periodicUpdateTasks.get(pageId);
        if (existing != null) {
            existing.cancel(false);
        }

        log.info("Starting periodic updates for page {}", pageId);

        // Initialize hash with current state
        try {
            Page page = pageService.getPage(session.notebookId(), session.chapterId(), pageId);
            if (page != null) {
                String initialHash = computePageHash(page);
                lastPageHashes.put(pageId, initialHash);
                log.debug("Initial page hash for {}: {}", pageId, initialHash);
            }
        } catch (Exception e) {
            log.error("Failed to compute initial page hash", e);
        }

        // Schedule periodic check at 500ms intervals
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!session.isConnected()) {
                    log.debug("Session disconnected, stopping periodic updates for page {}", pageId);
                    stopPeriodicUpdates(pageId);
                    return;
                }

                // Load current page state
                Page page = pageService.getPage(session.notebookId(), session.chapterId(), pageId);
                if (page == null) {
                    log.warn("Page not found during periodic update: {}", pageId);
                    return;
                }

                // Compute hash of current page state
                String currentHash = computePageHash(page);
                String lastHash = lastPageHashes.get(pageId);

                // Only send if content changed
                if (!currentHash.equals(lastHash)) {
                    log.debug("Page content changed (hash: {} -> {}), sending update", lastHash, currentHash);

                    // Send update
                    Map<String, Object> message = new HashMap<>();
                    message.put("type", "page_update");
                    message.put("page", page);

                    String json = jsonb.toJson(message);
                    sendMessage(session, json);

                    // Update stored hash
                    lastPageHashes.put(pageId, currentHash);
                    log.trace("Sent periodic update for page {}", pageId);
                } else {
                    log.trace("Page content unchanged, skipping update for {}", pageId);
                }

            } catch (Exception e) {
                log.error("Error during periodic update for page {}", pageId, e);
            }
        }, UPDATE_DEBOUNCE_MS, UPDATE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);

        periodicUpdateTasks.put(pageId, task);
    }

    /**
     * Stop periodic updates for a page.
     */
    private void stopPeriodicUpdates(String pageId) {
        ScheduledFuture<?> task = periodicUpdateTasks.remove(pageId);
        if (task != null) {
            task.cancel(false);
            log.info("Stopped periodic updates for page {}", pageId);
        }
        lastPageHashes.remove(pageId);
    }

    /**
     * Compute a hash of the page content for change detection.
     * Uses a simple hash of the JSON representation.
     */
    private String computePageHash(Page page) {
        try {
            String json = jsonb.toJson(page);
            return String.valueOf(json.hashCode());
        } catch (Exception e) {
            log.error("Failed to compute page hash", e);
            return String.valueOf(System.currentTimeMillis()); // Fallback to always different
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
            RTCDataChannelBuffer dcBuffer = new RTCDataChannelBuffer(buffer, true);  // true = binary message
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
