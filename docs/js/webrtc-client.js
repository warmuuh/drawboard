/**
 * WebRTC client for receiving page data from Drawboard desktop app.
 * Handles peer connection, data channel, and message processing.
 */
class WebRTCClient {
    constructor() {
        this.peerConnection = null;
        this.dataChannel = null;
        this.onPageData = null;
        this.onImageData = null;
        this.onPageUpdate = null;
        this.onConnectionStateChange = null;
        this.connectionState = 'disconnected';
    }

    /**
     * Connect to the desktop app using the offer SDP.
     * @param {string} offerSdp The offer SDP from the desktop app
     * @returns {Promise<string>} The answer SDP to send back
     */
    async connectWithOffer(offerSdp) {
        try {
            // Create peer connection with STUN server
            const config = {
                iceServers: [
                    { urls: 'stun:stun.l.google.com:19302' }
                ]
            };

            this.peerConnection = new RTCPeerConnection(config);

            // Set up connection state monitoring
            this.peerConnection.onconnectionstatechange = () => {
                this.connectionState = this.peerConnection.connectionState;
                console.log('Connection state:', this.connectionState);

                if (this.onConnectionStateChange) {
                    this.onConnectionStateChange(this.connectionState);
                }
            };

            // Set up ICE candidate handling
            this.peerConnection.onicecandidate = (event) => {
                if (event.candidate) {
                    console.log('ICE candidate:', event.candidate.candidate);
                } else {
                    console.log('ICE gathering complete');
                }
            };

            // Set up data channel handling
            this.peerConnection.ondatachannel = (event) => {
                console.log('Data channel received:', event.channel.label);
                this.dataChannel = event.channel;
                this.setupDataChannel();
            };

            // Set remote description (offer)
            const offer = {
                type: 'offer',
                sdp: offerSdp
            };
            await this.peerConnection.setRemoteDescription(offer);
            console.log('Remote description (offer) set');

            // Create answer
            const answer = await this.peerConnection.createAnswer();
            await this.peerConnection.setLocalDescription(answer);
            console.log('Local description (answer) set - waiting for ICE gathering');

            // Wait for ICE gathering to complete
            await this.waitForIceGathering();
            console.log('ICE gathering complete - answer includes all candidates');

            // Return the answer SDP with all ICE candidates
            return this.peerConnection.localDescription.sdp;

        } catch (error) {
            console.error('Failed to connect with offer:', error);
            throw error;
        }
    }

    /**
     * Wait for ICE gathering to complete.
     * This ensures the answer SDP includes all ICE candidates.
     */
    async waitForIceGathering() {
        return new Promise((resolve) => {
            // If already complete, resolve immediately
            if (this.peerConnection.iceGatheringState === 'complete') {
                resolve();
                return;
            }

            // Wait for gathering to complete
            const checkState = () => {
                if (this.peerConnection.iceGatheringState === 'complete') {
                    this.peerConnection.removeEventListener('icegatheringstatechange', checkState);
                    resolve();
                }
            };

            this.peerConnection.addEventListener('icegatheringstatechange', checkState);

            // Timeout after 10 seconds
            setTimeout(() => {
                console.warn('ICE gathering timeout - proceeding anyway');
                this.peerConnection.removeEventListener('icegatheringstatechange', checkState);
                resolve();
            }, 10000);
        });
    }

    /**
     * Set up the data channel for receiving messages.
     */
    setupDataChannel() {
        if (!this.dataChannel) {
            console.warn('No data channel to set up');
            return;
        }

        this.dataChannel.onopen = () => {
            console.log('Data channel opened');
            this.connectionState = 'connected';
            if (this.onConnectionStateChange) {
                this.onConnectionStateChange('connected');
            }
        };

        this.dataChannel.onclose = () => {
            console.log('Data channel closed');
            this.connectionState = 'disconnected';
            if (this.onConnectionStateChange) {
                this.onConnectionStateChange('disconnected');
            }
        };

        this.dataChannel.onerror = (error) => {
            console.error('Data channel error:', error);
        };

        this.dataChannel.onmessage = (event) => {
            this.handleMessage(event.data);
        };
    }

    /**
     * Handle incoming messages from the data channel.
     * @param {string} data The message data (JSON string)
     */
    handleMessage(data) {
        try {
            const message = JSON.parse(data);
            console.log('Received message:', message.type);

            switch (message.type) {
                case 'page_data':
                    if (this.onPageData) {
                        this.onPageData(message.page);
                    }
                    break;

                case 'image_data':
                    if (this.onImageData) {
                        this.onImageData(message.filename, message.data, message.mimeType);
                    }
                    break;

                case 'page_update':
                    if (this.onPageUpdate) {
                        this.onPageUpdate(message.page);
                    }
                    break;

                default:
                    console.warn('Unknown message type:', message.type);
            }

        } catch (error) {
            console.error('Failed to handle message:', error);
        }
    }

    /**
     * Close the connection.
     */
    close() {
        if (this.dataChannel) {
            this.dataChannel.close();
            this.dataChannel = null;
        }

        if (this.peerConnection) {
            this.peerConnection.close();
            this.peerConnection = null;
        }

        this.connectionState = 'disconnected';
        console.log('Connection closed');
    }

    /**
     * Get the current connection state.
     * @returns {string} The connection state
     */
    getConnectionState() {
        return this.connectionState;
    }
}

// Make available globally
window.WebRTCClient = WebRTCClient;
