/**
 * Main application logic for Drawboard Viewer.
 * Coordinates PeerJS connection, rendering, and UI updates.
 */
class DrawboardViewerApp {
    constructor() {
        this.peerClient = null;
        this.pageRenderer = null;
        this.currentZoom = 1.0;
        this.pendingUpdate = false;

        this.initializeUI();
        this.initializeCanvas();
        this.setupVisibilityHandler();
        this.checkForPeerId();
    }

    initializeUI() {
        this.elements = {
            connectionSection: document.getElementById('connectionSection'),
            canvasSection: document.getElementById('canvasSection'),
            connectionMessage: document.getElementById('connectionMessage'),
            progressFill: document.getElementById('progressFill'),
            statusDot: document.getElementById('statusDot'),
            statusText: document.getElementById('statusText'),
            zoomInBtn: document.getElementById('zoomInBtn'),
            zoomOutBtn: document.getElementById('zoomOutBtn'),
            resetZoomBtn: document.getElementById('resetZoomBtn'),
            zoomLevel: document.getElementById('zoomLevel')
        };

        this.elements.zoomInBtn.addEventListener('click', () => this.zoomIn());
        this.elements.zoomOutBtn.addEventListener('click', () => this.zoomOut());
        this.elements.resetZoomBtn.addEventListener('click', () => this.resetZoom());
    }

    initializeCanvas() {
        const canvasWrapper = document.getElementById('canvasWrapper');
        this.pageRenderer = new PageRenderer(canvasWrapper);
    }

    setupVisibilityHandler() {
        document.addEventListener('visibilitychange', () => {
            if (!document.hidden && this.pendingUpdate) {
                console.log('Tab became visible, forcing refresh');
                if (this.pageRenderer && this.pageRenderer.currentPage) {
                    this.pageRenderer.renderPage(this.pageRenderer.currentPage);
                    this.pageRenderer.forceReflow();
                }
                this.pendingUpdate = false;
            }
        });
    }

    /**
     * Check if URL contains peer ID and auto-connect.
     */
    async checkForPeerId() {
        const params = new URLSearchParams(window.location.search);
        const peerId = params.get('peerId');

        if (!peerId) {
            this.showError('No peer ID found in URL. Please use the complete link provided by the sender.');
            return;
        }

        console.log('Found peer ID:', peerId);
        this.updateConnectionMessage('Connecting to desktop app...');
        this.animateProgress(0, 30, 1000);

        await this.connectToDesktop(peerId);
    }

    /**
     * Connect to the desktop app using peer ID.
     */
    async connectToDesktop(peerId) {
        try {
            this.peerClient = new PeerJSClient();

            this.peerClient.onConnectionStateChange = (state) => this.updateConnectionStatus(state);
            this.peerClient.onPageData = (page) => this.handlePageData(page);
            this.peerClient.onImageData = (filename, data, mimeType) => this.handleImageData(filename, data, mimeType);
            this.peerClient.onPageUpdate = (page) => this.handlePageUpdate(page);

            this.updateConnectionMessage('Establishing peer connection...');
            this.animateProgress(30, 60, 2000);

            await this.peerClient.connectToPeer(peerId);

            this.updateConnectionMessage('Connected! Waiting for page data...');
            this.animateProgress(60, 100, 500);

        } catch (error) {
            console.error('Failed to connect:', error);
            this.showError(`Connection failed: ${error.message}`);
        }
    }

    updateConnectionMessage(message) {
        if (this.elements.connectionMessage) {
            this.elements.connectionMessage.textContent = message;
        }
    }

    animateProgress(from, to, duration) {
        const progressFill = this.elements.progressFill;
        if (!progressFill) return;

        const startTime = Date.now();
        const animate = () => {
            const elapsed = Date.now() - startTime;
            const progress = Math.min(elapsed / duration, 1);
            const value = from + (to - from) * progress;
            progressFill.style.width = value + '%';

            if (progress < 1) {
                requestAnimationFrame(animate);
            }
        };
        animate();
    }

    showError(message) {
        this.elements.connectionMessage.textContent = message;
        this.elements.connectionMessage.style.color = '#c62828';
        this.elements.progressFill.style.backgroundColor = '#c62828';
    }

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

    handlePageData(page) {
        console.log('Received page data:', page);

        this.elements.connectionSection.style.display = 'none';
        this.elements.canvasSection.style.display = 'flex';

        this.elements.statusDot.classList.add('connected');
        this.elements.statusText.textContent = `Viewing: ${page.name}`;

        this.pageRenderer.renderPage(page);
    }

    handleImageData(filename, data, mimeType) {
        console.log('Received image:', filename);
        this.pageRenderer.addImage(filename, data, mimeType);
    }

    handlePageUpdate(page) {
        console.log('Received page update:', page);
        this.elements.statusText.textContent = `Viewing: ${page.name} (updated)`;

        if (document.hidden) {
            this.pendingUpdate = true;
            console.log('Tab hidden, update will be applied when visible');
        }

        requestAnimationFrame(() => {
            this.pageRenderer.renderPage(page);
            requestAnimationFrame(() => {
                this.pageRenderer.forceReflow();
            });
        });
    }

    zoomIn() {
        this.currentZoom = Math.min(5.0, this.currentZoom + 0.25);
        this.pageRenderer.setZoom(this.currentZoom);
        this.updateZoomLevel();
    }

    zoomOut() {
        this.currentZoom = Math.max(0.25, this.currentZoom - 0.25);
        this.pageRenderer.setZoom(this.currentZoom);
        this.updateZoomLevel();
    }

    resetZoom() {
        this.currentZoom = 1.0;
        this.pageRenderer.setZoom(this.currentZoom);
        this.updateZoomLevel();
    }

    updateZoomLevel() {
        this.elements.zoomLevel.textContent = `${Math.round(this.currentZoom * 100)}%`;
    }
}

document.addEventListener('DOMContentLoaded', () => {
    console.log('Drawboard Viewer initializing...');
    new DrawboardViewerApp();
});
