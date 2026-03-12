package com.drawboard.webrtc;

import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCPeerConnection;

import java.time.Instant;

/**
 * Holds state for an active page sharing session.
 */
public class PageShareSession {
    private final String sessionId;
    private final String notebookId;
    private final String chapterId;
    private final String pageId;
    private final RTCPeerConnection peerConnection;
    private RTCDataChannel dataChannel;
    private boolean connected;
    private final Instant createdAt;

    public PageShareSession(String sessionId, String notebookId, String chapterId,
                           String pageId, RTCPeerConnection peerConnection) {
        this.sessionId = sessionId;
        this.notebookId = notebookId;
        this.chapterId = chapterId;
        this.pageId = pageId;
        this.peerConnection = peerConnection;
        this.connected = false;
        this.createdAt = Instant.now();
    }

    public String sessionId() {
        return sessionId;
    }

    public String notebookId() {
        return notebookId;
    }

    public String chapterId() {
        return chapterId;
    }

    public String pageId() {
        return pageId;
    }

    public RTCPeerConnection peerConnection() {
        return peerConnection;
    }

    public RTCDataChannel dataChannel() {
        return dataChannel;
    }

    public void setDataChannel(RTCDataChannel dataChannel) {
        this.dataChannel = dataChannel;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
