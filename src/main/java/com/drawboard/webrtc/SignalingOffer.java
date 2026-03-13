package com.drawboard.webrtc;

/**
 * Represents an incoming offer from a viewer peer.
 * Includes the connection ID which is required by PeerJS protocol.
 */
public record SignalingOffer(
    String fromPeerId,
    String connectionId,
    String sdp
) {}
