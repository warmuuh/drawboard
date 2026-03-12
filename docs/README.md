# Drawboard Viewer

A web-based viewer for real-time page sharing from the Drawboard desktop application via WebRTC.

## Overview

This viewer allows you to view Drawboard pages in real-time as they are edited in the desktop application. The connection is peer-to-peer using WebRTC, with no server infrastructure required.

## How to Use

1. **Receive Share Link**: The person sharing their Drawboard page will send you a link
2. **Open Link**: Open the link in your web browser
3. **Copy Answer**: An "answer" will be generated automatically - copy it
4. **Send Answer**: Send the answer back to the person sharing
5. **View Page**: Once they paste your answer, the page will load and update in real-time

## Features

- **Real-time Updates**: See changes as they happen with 500ms debouncing
- **Full Fidelity**: Supports text, images, and freehand drawings
- **Zoom Controls**: Zoom in/out and pan around the canvas
- **Responsive Design**: Works on desktop and mobile browsers
- **No Account Required**: Simple peer-to-peer connection

## Technical Details

- **WebRTC**: Peer-to-peer connection with DTLS encryption
- **Data Channel**: JSON messages for page data and updates
- **Canvas Rendering**: HTML5 canvas for high-quality rendering
- **Compression**: GZIP compression for connection URLs

## Browser Compatibility

- Chrome/Edge (recommended)
- Firefox
- Safari
- Opera

All modern browsers with WebRTC support.

## Deployment

This viewer is designed to be hosted on GitHub Pages:

1. Push the `docs/` folder to your repository
2. Enable GitHub Pages from the repository settings
3. Select the `main` branch and `/docs` folder
4. Access at `https://[username].github.io/[repo]/`

## Development

### File Structure

```
docs/
├── index.html          # Main viewer page
├── css/
│   └── viewer.css     # Styling
├── js/
│   ├── app.js         # Main application logic
│   ├── webrtc-client.js   # WebRTC connection management
│   ├── page-renderer.js   # Canvas rendering
│   └── offer-decoder.js   # URL parameter decoding
└── README.md          # This file
```

### Dependencies

- **pako.js**: GZIP decompression (loaded from CDN)
- Native browser WebRTC APIs

### Local Testing

Simply open `index.html` in a web browser. For full testing, you'll need to:

1. Generate a share link from the Drawboard desktop app
2. Open the link in your browser
3. Complete the connection process

## Security

- All connections are encrypted with DTLS (WebRTC standard)
- Read-only viewer - cannot modify the shared page
- No data passes through any server (peer-to-peer)
- Session-based - connections are temporary

## Troubleshooting

### Connection Issues

- **Firewall**: Strict firewalls may block WebRTC connections
- **Browser Support**: Ensure your browser supports WebRTC
- **STUN Server**: Uses Google's public STUN server for NAT traversal

### No Page Appearing

- Check browser console for errors
- Verify the answer was sent correctly
- Ensure the desktop app is still running

## Future Enhancements

- [ ] QR code for easier sharing
- [ ] Session recording
- [ ] Multi-page navigation
- [ ] Comment/annotation system
- [ ] Dark mode
- [ ] Touch gesture support for mobile

## License

Part of the Drawboard project.
