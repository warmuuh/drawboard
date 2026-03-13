/**
 * PeerJS client for connecting to Drawboard desktop app.
 * Uses PeerJS for automatic signaling and connection.
 */
class PeerJSClient {
    constructor() {
        this.peer = null;
        this.connection = null;
        this.onPageData = null;
        this.onImageData = null;
        this.onPageUpdate = null;
        this.onConnectionStateChange = null;
        this.connectionState = 'disconnected';
    }

    /**
     * Connect to desktop app using peer ID.
     * @param {string} desktopPeerId The desktop app's peer ID
     */
    async connectToPeer(desktopPeerId) {
        try {
            console.log('Creating PeerJS instance...');

            // Create our peer connected to public PeerJS cloud
            this.peer = new Peer();

            // Wait for peer to be ready
            await new Promise((resolve, reject) => {
                this.peer.on('open', (id) => {
                    console.log('Our peer ID:', id);
                    resolve();
                });

                this.peer.on('error', (error) => {
                    console.error('PeerJS error:', error);
                    reject(error);
                });
            });

            console.log('Connecting to desktop peer:', desktopPeerId);

            // Connect to desktop peer
            this.connection = this.peer.connect(desktopPeerId, {
                reliable: true,
                serialization: 'json'
            });

            // Set up connection handlers
            this.setupConnectionHandlers();

        } catch (error) {
            console.error('Failed to connect to peer:', error);
            throw error;
        }
    }

    setupConnectionHandlers() {
        if (!this.connection) return;

        this.connection.on('open', () => {
            console.log('Data channel opened!');
            this.connectionState = 'connected';

            if (this.onConnectionStateChange) {
                this.onConnectionStateChange('connected');
            }
        });

        this.connection.on('data', (data) => {
            // Desktop sends binary data (ArrayBuffer), need to decode to JSON
            if (data instanceof ArrayBuffer) {
                const text = new TextDecoder('utf-8').decode(data);
                console.log('Received binary data, decoded to text:', text.substring(0, 100));
                try {
                    const message = JSON.parse(text);
                    this.handleMessage(message);
                } catch (error) {
                    console.error('Failed to parse JSON from binary data:', error);
                }
            } else if (typeof data === 'string') {
                // If data comes as string, parse it
                console.log('Received string data:', data.substring(0, 100));
                try {
                    const message = JSON.parse(data);
                    this.handleMessage(message);
                } catch (error) {
                    console.error('Failed to parse JSON from string:', error);
                }
            } else {
                // If data is already an object, use it directly
                console.log('Received object data:', data);
                this.handleMessage(data);
            }
        });

        this.connection.on('close', () => {
            console.log('Data channel closed');
            this.connectionState = 'disconnected';

            if (this.onConnectionStateChange) {
                this.onConnectionStateChange('disconnected');
            }
        });

        this.connection.on('error', (error) => {
            console.error('Connection error:', error);
            this.connectionState = 'failed';

            if (this.onConnectionStateChange) {
                this.onConnectionStateChange('failed');
            }
        });
    }

    handleMessage(message) {
        console.log('Received message type:', message.type);

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
    }

    close() {
        if (this.connection) {
            this.connection.close();
            this.connection = null;
        }

        if (this.peer) {
            this.peer.destroy();
            this.peer = null;
        }

        this.connectionState = 'disconnected';
        console.log('Connection closed');
    }

    getConnectionState() {
        return this.connectionState;
    }
}

window.PeerJSClient = PeerJSClient;
