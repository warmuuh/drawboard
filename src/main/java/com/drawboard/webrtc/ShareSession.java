package com.drawboard.webrtc;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Represents a WebRTC share session with peer ID.
 * Replaces the old ShareOffer which contained the full SDP.
 */
public record ShareSession(
    String peerId,
    String shareUrl
) {
    /**
     * Generate a share URL with the peer ID.
     */
    public static String generateShareUrl(String baseUrl, String peerId) {
        String encodedPeerId = URLEncoder.encode(peerId, StandardCharsets.UTF_8);
        return baseUrl + "?peerId=" + encodedPeerId;
    }
}
