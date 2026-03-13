package com.drawboard.webrtc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * WebSocket client for connecting to PeerJS server (0.peerjs.com).
 * Handles signaling for offer/answer exchange.
 */
public class PeerJSWebSocketClient extends WebSocketClient {
    private static final Logger log = LoggerFactory.getLogger(PeerJSWebSocketClient.class);
    private static final String PEERJS_SERVER = "wss://0.peerjs.com/peerjs";
    private static final String PEERJS_KEY = "peerjs";

    private final ObjectMapper objectMapper;
    private final String peerId;
    private final String token;
    private Consumer<SignalingOffer> onOfferReceived;
    private CountDownLatch connectLatch;

    public PeerJSWebSocketClient(String peerId, Consumer<SignalingOffer> onOfferReceived) {
        super(buildUri(peerId));
        this.peerId = peerId;
        this.token = UUID.randomUUID().toString();
        this.onOfferReceived = onOfferReceived;
        this.objectMapper = new ObjectMapper();
        this.connectLatch = new CountDownLatch(1);
    }

    private static URI buildUri(String peerId) {
        String token = UUID.randomUUID().toString();
        String url = String.format("%s?key=%s&id=%s&token=%s",
            PEERJS_SERVER, PEERJS_KEY, peerId, token);
        return URI.create(url);
    }

    public boolean connectAndWait(long timeout, TimeUnit unit) throws InterruptedException {
        this.connect();
        return connectLatch.await(timeout, unit);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("Connected to PeerJS server with peer ID: {}", peerId);
        connectLatch.countDown();
    }

    @Override
    public void onMessage(String message) {
        try {
            log.debug("Received message: {}", message);
            JsonNode json = objectMapper.readTree(message);
            String type = json.get("type").asText();

            switch (type) {
                case "OPEN" -> {
                    log.info("PeerJS server acknowledged connection");
                }
                case "OFFER" -> {
                    String from = json.get("src").asText();
                    JsonNode payload = json.get("payload");
                    String connectionId = payload.get("connectionId").asText();
                    String sdp = payload.get("sdp").get("sdp").asText();

                    log.info("Received offer from peer: {} (connection: {})", from, connectionId);
                    if (onOfferReceived != null) {
                        SignalingOffer offer = new SignalingOffer(from, connectionId, sdp);
                        onOfferReceived.accept(offer);
                    }
                }
                case "CANDIDATE" -> {
                    // ICE candidates - just log for debugging
                    // webrtc-java handles these automatically after setRemoteDescription
                    String from = json.get("src").asText();
                    JsonNode candidate = json.get("payload").get("candidate");
                    log.debug("Received ICE candidate from {}: {}", from, candidate.get("candidate").asText());
                }
                case "EXPIRE" -> {
                    log.warn("Connection to PeerJS server expired");
                }
                case "ERROR" -> {
                    String error = json.has("msg") ? json.get("msg").asText() : "Unknown error";
                    log.error("PeerJS error: {}", error);
                }
                default -> {
                    log.debug("Unhandled message type: {}", type);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process message", e);
        }
    }

    /**
     * Send answer back to viewer peer.
     */
    public void sendAnswer(String toPeerId, String connectionId, String answerSdp) {
        try {
            ObjectNode message = objectMapper.createObjectNode();
            message.put("type", "ANSWER");
            message.put("dst", toPeerId);

            ObjectNode payload = message.putObject("payload");
            payload.put("connectionId", connectionId);
            payload.put("type", "data");
            payload.put("serialization", "binary");

            ObjectNode sdp = payload.putObject("sdp");
            sdp.put("type", "answer");
            sdp.put("sdp", answerSdp);

            String json = objectMapper.writeValueAsString(message);
            log.info("Sending answer to peer: {} (connection: {})", toPeerId, connectionId);
            this.send(json);
        } catch (Exception e) {
            log.error("Failed to send answer", e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.info("Disconnected from PeerJS server: {} (code: {})", reason, code);
    }

    @Override
    public void onError(Exception ex) {
        log.error("WebSocket error", ex);
    }
}
