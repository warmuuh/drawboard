/**
 * Decodes the offer SDP from the URL parameter.
 * The offer is compressed with GZIP and base64-encoded.
 */
class OfferDecoder {
    /**
     * Extract and decode the offer from the current URL.
     * @returns {string|null} The decoded offer SDP, or null if not found
     */
    static decodeOfferFromUrl() {
        try {
            const params = new URLSearchParams(window.location.search);
            const encodedOffer = params.get('offer');

            if (!encodedOffer) {
                console.warn('No offer parameter found in URL');
                return null;
            }

            if (encodedOffer.trim() === '') {
                console.warn('Offer parameter is empty');
                return null;
            }

            // URL decode
            const urlDecoded = decodeURIComponent(encodedOffer);

            // Convert URL-safe Base64 to standard Base64
            // Replace - with + and _ with /, then add padding if needed
            let standardBase64 = urlDecoded.replace(/-/g, '+').replace(/_/g, '/');

            // Add padding if necessary
            while (standardBase64.length % 4) {
                standardBase64 += '=';
            }

            // Base64 decode
            const base64Decoded = atob(standardBase64);

            // Convert to Uint8Array
            const compressed = new Uint8Array(base64Decoded.length);
            for (let i = 0; i < base64Decoded.length; i++) {
                compressed[i] = base64Decoded.charCodeAt(i);
            }

            // GZIP decompress using pako
            const decompressed = pako.inflate(compressed, { to: 'string' });

            console.log('Successfully decoded offer from URL');
            return decompressed;

        } catch (error) {
            console.error('Failed to decode offer from URL:', error);
            return null;
        }
    }

    /**
     * Check if the URL contains an offer parameter.
     * @returns {boolean} True if offer parameter exists
     */
    static hasOffer() {
        const params = new URLSearchParams(window.location.search);
        return params.has('offer');
    }
}

// Make available globally
window.OfferDecoder = OfferDecoder;
