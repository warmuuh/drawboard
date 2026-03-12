/**
 * Main application logic for Drawboard Viewer.
 * Coordinates WebRTC connection, rendering, and UI updates.
 */
class DrawboardViewerApp {
    constructor() {
        this.webrtcClient = null;
        this.pageRenderer = null;
        this.currentZoom = 1.0;
        this.pendingUpdate = false;

        this.initializeUI();
        this.initializeCanvas();
        this.setupVisibilityHandler();
        this.checkForOffer();
    }

    /**
     * Initialize UI elements and event handlers.
     */
    initializeUI() {
        // Get UI elements
        this.elements = {
            connectionSection: document.getElementById('connectionSection'),
            canvasSection: document.getElementById('canvasSection'),
            offerStatus: document.getElementById('offerStatus'),
            answerText: document.getElementById('answerText'),
            copyAnswerBtn: document.getElementById('copyAnswerBtn'),
            statusDot: document.getElementById('statusDot'),
            statusText: document.getElementById('statusText'),
            zoomInBtn: document.getElementById('zoomInBtn'),
            zoomOutBtn: document.getElementById('zoomOutBtn'),
            resetZoomBtn: document.getElementById('resetZoomBtn'),
            zoomLevel: document.getElementById('zoomLevel')
        };

        // Set up event handlers
        this.elements.copyAnswerBtn.addEventListener('click', () => this.copyAnswer());
        this.elements.zoomInBtn.addEventListener('click', () => this.zoomIn());
        this.elements.zoomOutBtn.addEventListener('click', () => this.zoomOut());
        this.elements.resetZoomBtn.addEventListener('click', () => this.resetZoom());
    }

    /**
     * Initialize the canvas and renderer.
     */
    initializeCanvas() {
        const canvasWrapper = document.getElementById('canvasWrapper');
        this.pageRenderer = new PageRenderer(canvasWrapper);
    }

    /**
     * Set up visibility change handler to force refresh when tab becomes visible.
     */
    setupVisibilityHandler() {
        document.addEventListener('visibilitychange', () => {
            if (!document.hidden && this.pendingUpdate) {
                console.log('Tab became visible, forcing refresh');
                // Force a full re-render when tab becomes visible
                if (this.pageRenderer && this.pageRenderer.currentPage) {
                    this.pageRenderer.renderPage(this.pageRenderer.currentPage);
                    this.pageRenderer.forceReflow();
                }
                this.pendingUpdate = false;
            }
        });
    }

    /**
     * Check if URL contains an offer and start connection.
     */
    async checkForOffer() {
        if (!OfferDecoder.hasOffer()) {
            this.elements.offerStatus.textContent = 'No offer found in URL. Please use the complete link provided by the sender.';
            this.elements.offerStatus.style.color = '#c62828';
            return;
        }

        const offerSdp = OfferDecoder.decodeOfferFromUrl();
        if (!offerSdp) {
            this.elements.offerStatus.textContent = 'Failed to decode offer from URL. The link may be incomplete or corrupted. Please request a new link from the sender.';
            this.elements.offerStatus.style.color = '#c62828';

            // Show more details in console
            console.error('Offer decoding failed. Check that:');
            console.error('1. The URL contains the complete offer parameter');
            console.error('2. The URL was not corrupted during copy/paste');
            console.error('3. You are using a modern browser with WebRTC support');
            return;
        }

        this.elements.offerStatus.textContent = 'Offer decoded successfully. Creating connection...';
        this.elements.offerStatus.style.color = '#4caf50';

        // Update status to show ICE gathering
        setTimeout(() => {
            if (!this.webrtcClient || !this.webrtcClient.getConnectionState() === 'connected') {
                this.elements.offerStatus.textContent = 'Gathering network information (ICE candidates)... This may take a few seconds.';
            }
        }, 1000);

        await this.connectToDesktop(offerSdp);
    }

    /**
     * Connect to the desktop app using the offer.
     * @param {string} offerSdp The offer SDP
     */
    async connectToDesktop(offerSdp) {
        try {
            // Create WebRTC client
            this.webrtcClient = new WebRTCClient();

            // Set up event handlers
            this.webrtcClient.onConnectionStateChange = (state) => this.updateConnectionStatus(state);
            this.webrtcClient.onPageData = (page) => this.handlePageData(page);
            this.webrtcClient.onImageData = (filename, data, mimeType) => this.handleImageData(filename, data, mimeType);
            this.webrtcClient.onPageUpdate = (page) => this.handlePageUpdate(page);

            // Connect and get answer
            const answerSdp = await this.webrtcClient.connectWithOffer(offerSdp);

            // Display answer for user to copy
            this.elements.answerText.value = answerSdp;
            this.elements.copyAnswerBtn.disabled = false;
            this.elements.offerStatus.textContent = 'Connection created! Copy the answer below and send it to the sender.';

            console.log('Answer generated successfully');

        } catch (error) {
            console.error('Failed to connect:', error);
            this.elements.offerStatus.textContent = `Connection failed: ${error.message}`;
            this.elements.offerStatus.style.color = '#c62828';
        }
    }

    /**
     * Update the connection status UI.
     * @param {string} state The connection state
     */
    updateConnectionStatus(state) {
        const statusDot = this.elements.statusDot;
        const statusText = this.elements.statusText;

        statusDot.className = 'status-dot';

        switch (state) {
            case 'connecting':
                statusText.textContent = 'Connecting...';
                break;

            case 'connected':
                statusDot.classList.add('connected');
                statusText.textContent = 'Connected - receiving page data';
                break;

            case 'disconnected':
                statusDot.classList.add('disconnected');
                statusText.textContent = 'Disconnected';
                break;

            case 'failed':
                statusDot.classList.add('disconnected');
                statusText.textContent = 'Connection failed';
                break;

            default:
                statusText.textContent = `Status: ${state}`;
        }
    }

    /**
     * Handle initial page data.
     * @param {Object} page The page data
     */
    handlePageData(page) {
        console.log('Received page data:', page);

        // Switch to canvas view
        this.elements.connectionSection.style.display = 'none';
        this.elements.canvasSection.style.display = 'flex';

        // Update status
        this.elements.statusDot.classList.add('connected');
        this.elements.statusText.textContent = `Viewing: ${page.name}`;

        // Render the page
        this.pageRenderer.renderPage(page);
    }

    /**
     * Handle image data.
     * @param {string} filename The image filename
     * @param {string} data The base64-encoded image data
     * @param {string} mimeType The image MIME type
     */
    handleImageData(filename, data, mimeType) {
        console.log('Received image:', filename);
        this.pageRenderer.addImage(filename, data, mimeType);
    }

    /**
     * Handle page update.
     * @param {Object} page The updated page data
     */
    handlePageUpdate(page) {
        console.log('Received page update:', page);
        this.elements.statusText.textContent = `Viewing: ${page.name} (updated)`;

        // Mark that we have a pending update
        if (document.hidden) {
            this.pendingUpdate = true;
            console.log('Tab hidden, update will be applied when visible');
        }

        // Use requestAnimationFrame to force render even when tab is not focused
        requestAnimationFrame(() => {
            this.pageRenderer.renderPage(page);

            // Force a reflow to ensure the update is processed
            this.pageRenderer.forceReflow();
        });
    }

    /**
     * Copy the answer to clipboard.
     */
    async copyAnswer() {
        const answerText = this.elements.answerText.value;

        try {
            await navigator.clipboard.writeText(answerText);

            // Visual feedback
            const btn = this.elements.copyAnswerBtn;
            const originalText = btn.textContent;
            btn.textContent = '✓ Copied!';
            btn.style.background = '#4caf50';

            setTimeout(() => {
                btn.textContent = originalText;
                btn.style.background = '';
            }, 2000);

        } catch (error) {
            console.error('Failed to copy to clipboard:', error);
            alert('Failed to copy. Please copy manually.');
        }
    }

    /**
     * Zoom in on the canvas.
     */
    zoomIn() {
        this.currentZoom = Math.min(5.0, this.currentZoom + 0.25);
        this.pageRenderer.setZoom(this.currentZoom);
        this.updateZoomLevel();
    }

    /**
     * Zoom out on the canvas.
     */
    zoomOut() {
        this.currentZoom = Math.max(0.25, this.currentZoom - 0.25);
        this.pageRenderer.setZoom(this.currentZoom);
        this.updateZoomLevel();
    }

    /**
     * Reset zoom to 100%.
     */
    resetZoom() {
        this.currentZoom = 1.0;
        this.pageRenderer.setZoom(this.currentZoom);
        this.updateZoomLevel();
    }

    /**
     * Update the zoom level display.
     */
    updateZoomLevel() {
        this.elements.zoomLevel.textContent = `${Math.round(this.currentZoom * 100)}%`;
    }
}

// Initialize the app when the page loads
document.addEventListener('DOMContentLoaded', () => {
    console.log('Drawboard Viewer initializing...');
    new DrawboardViewerApp();
});
