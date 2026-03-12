package com.drawboard.webrtc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

/**
 * Represents a WebRTC share offer with SDP and generated share URL.
 */
public record ShareOffer(
    String sessionId,
    String offerSdp,
    String shareUrl
) {
    /**
     * Generate a share URL with compressed and encoded offer SDP.
     *
     * @param baseUrl The base URL of the viewer (e.g., GitHub Pages URL)
     * @param offerSdp The SDP offer string
     * @return Complete share URL with encoded offer parameter
     */
    public static String generateShareUrl(String baseUrl, String offerSdp) {
        try {
            // GZIP compress the SDP
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
                gzos.write(offerSdp.getBytes(StandardCharsets.UTF_8));
            }
            byte[] compressed = baos.toByteArray();

            // Base64 encode
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(compressed);

            // URL encode for safety
            String urlEncoded = URLEncoder.encode(encoded, StandardCharsets.UTF_8);

            // Construct full URL
            return baseUrl + "?offer=" + urlEncoded;
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate share URL", e);
        }
    }
}
