# WebRTC Page Sharing Implementation

## Overview

This document describes the complete implementation of real-time page sharing from the Drawboard desktop application to web viewers via WebRTC.

## Implementation Status: ✅ COMPLETE

All phases of the implementation plan have been completed successfully.

---

## Architecture

### High-Level Flow

```
Desktop App (Java/JavaFX)          Web Viewer (Browser)
━━━━━━━━━━━━━━━━━━━━━━━━━         ━━━━━━━━━━━━━━━━━━━━
1. User clicks "Share"
2. Generate WebRTC offer
3. Compress & encode in URL ────────▶ 4. Open URL
5. Display share URL                  5. Decode offer
                                      6. Create answer
7. User pastes answer ◀────────────── 7. Display answer
8. Process answer
9. WebRTC connection established ◀──▶ 9. Connection established
10. Send page data ─────────────────▶ 10. Render page
11. Send images ────────────────────▶ 11. Load images
12. User edits page
13. Auto-detect changes (500ms)
14. Send updates ───────────────────▶ 14. Re-render
```

---

## Desktop Application (Java)

### 1. Dependencies Added

**pom.xml:**
```xml
<!-- WebRTC for Java -->
<dependency>
    <groupId>dev.onvoid.webrtc</groupId>
    <artifactId>webrtc-java</artifactId>
    <version>0.14.0</version>
</dependency>

<!-- Jakarta Annotations -->
<dependency>
    <groupId>jakarta.annotation</groupId>
    <artifactId>jakarta.annotation-api</artifactId>
    <version>3.0.0</version>
</dependency>
```

### 2. Module Configuration

**module-info.java:**
```java
requires webrtc.java;
requires jakarta.annotation;

exports com.drawboard.webrtc;
opens com.drawboard.webrtc to io.avaje.jsonb;
```

### 3. Core Components

#### WebRTCShareService
**Location:** `src/main/java/com/drawboard/service/WebRTCShareService.java`

**Key Features:**
- Singleton service with DI integration
- PeerConnectionFactory initialization
- Offer/answer SDP handling
- Data channel management
- Debounced update propagation (500ms)
- Image transfer as base64
- Session lifecycle management

**Key Methods:**
```java
public ShareOffer createShareOffer(String notebookId, String chapterId, String pageId)
public void processAnswer(String pageId, String answerSdp)
public void stopSharing(String pageId)
public boolean isSharing(String pageId)
```

#### Supporting Classes

**PageShareSession** (`webrtc/PageShareSession.java`):
- Holds session state (IDs, peer connection, data channel)
- Tracks connection status

**ShareOffer** (`webrtc/ShareOffer.java`):
- Record containing sessionId, offerSdp, shareUrl
- Generates compressed share URLs (GZIP + Base64)

**PageUpdateEvent** (`webrtc/PageUpdateEvent.java`):
- Event record for page changes
- Contains notebookId, chapterId, pageId, page data

### 4. PageService Integration

**Modified:** `src/main/java/com/drawboard/service/PageService.java`

**Added:**
- `CopyOnWriteArrayList` for update listeners
- `addUpdateListener(Consumer<PageUpdateEvent>)` method
- `removeUpdateListener(Consumer<PageUpdateEvent>)` method
- `notifyUpdateListeners()` called after all save operations

### 5. UI Components

#### ShareDialog

**FXML:** `src/main/resources/fxml/ShareDialog.fxml`
- Share URL display with copy button
- Answer text area
- Connect button
- Connection status label
- Stop sharing button

**Controller:** `src/main/java/com/drawboard/ui/ShareDialogController.java`
- Displays share offer
- Handles clipboard operations
- Processes answer SDP
- Real-time status monitoring (500ms polling)
- Connection lifecycle management

#### MainWindow Integration

**Modified:** `src/main/resources/fxml/MainWindow.fxml`
- Added "Share" button to toolbar

**Modified:** `src/main/java/com/drawboard/ui/MainWindowController.java`
- Injected WebRTCShareService
- Added `handleSharePage()` method
- Share button enabled/disabled based on page load state
- Added `showAlert()` helper method

**Modified:** `src/main/java/com/drawboard/app/DrawboardApplication.java`
- Added WebRTCShareService to controller factory

---

## Web Viewer (HTML/JavaScript)

### File Structure

```
docs/
├── index.html              # Main viewer page
├── README.md              # Documentation
├── css/
│   └── viewer.css         # Styling
└── js/
    ├── app.js             # Application logic
    ├── webrtc-client.js   # WebRTC client
    ├── page-renderer.js   # Canvas rendering
    └── offer-decoder.js   # URL decoding
```

### 1. index.html

**Features:**
- Two-section layout (connection → canvas)
- Connection instructions
- Answer display and copy
- Canvas with zoom controls
- Responsive design

**External Dependencies:**
- pako.js (GZIP decompression from CDN)

### 2. CSS (viewer.css)

**Features:**
- Modern gradient background
- Card-based UI design
- Status indicators (dots with colors)
- Responsive layout
- Button animations
- Loading states

### 3. JavaScript Modules

#### OfferDecoder (offer-decoder.js)

**Purpose:** Decode compressed offer from URL parameter

**Key Methods:**
```javascript
static decodeOfferFromUrl()  // Returns decoded SDP
static hasOffer()             // Check if URL has offer
```

**Process:**
1. Extract `offer` parameter from URL
2. URL decode
3. Base64 decode
4. GZIP decompress with pako.js

#### WebRTCClient (webrtc-client.js)

**Purpose:** Manage WebRTC peer connection and data channel

**Key Methods:**
```javascript
async connectWithOffer(offerSdp)  // Returns answer SDP
setupDataChannel()                 // Configure data channel
handleMessage(data)                // Process incoming messages
close()                            // Clean up connection
```

**Message Types Handled:**
- `page_data` → Initial page structure
- `image_data` → Base64 image data
- `page_update` → Updated page structure

#### PageRenderer (page-renderer.js)

**Purpose:** Render page elements on HTML5 canvas

**Key Methods:**
```javascript
renderPage(pageData)              // Render complete page
renderElement(element)            // Render single element
addImage(filename, data, type)    // Add image to cache
setZoom(scale)                    // Set zoom level
```

**Element Types Supported:**
- `TEXT` → Text with word wrapping
- `IMAGE` → Image with caching
- `DRAWING` → Freehand paths

#### App (app.js)

**Purpose:** Main application logic and coordination

**Key Responsibilities:**
- Initialize UI and canvas
- Check for offer in URL
- Create WebRTC connection
- Handle page data/updates
- Manage zoom controls
- Update connection status

---

## Data Transfer Protocol

### Message Format

All messages sent as JSON over WebRTC data channel:

#### 1. Initial Page Data
```json
{
  "type": "page_data",
  "page": {
    "id": "uuid",
    "name": "Page Name",
    "created": "2026-03-12T10:00:00Z",
    "modified": "2026-03-12T11:00:00Z",
    "elements": [
      {
        "@type": "TEXT",
        "id": "elem-1",
        "x": 100, "y": 200,
        "width": 400, "height": 100,
        "htmlContent": "<p>Hello</p>",
        "zIndex": 0
      }
    ]
  }
}
```

#### 2. Image Data
```json
{
  "type": "image_data",
  "filename": "image-uuid.png",
  "data": "base64-encoded-png-data",
  "mimeType": "image/png"
}
```

#### 3. Page Update
```json
{
  "type": "page_update",
  "page": { /* same structure as page_data */ }
}
```

---

## User Workflow

### Desktop App (Sender)

1. Open a page in Drawboard
2. Click "Share" button in toolbar
3. Copy the generated share URL
4. Send URL to viewer via messaging app
5. Wait for viewer to send back answer
6. Paste answer in dialog
7. Click "Connect"
8. Page is now being shared in real-time
9. Any edits appear in viewer after 500ms
10. Click "Stop Sharing" to end session

### Web Viewer (Receiver)

1. Receive share URL from sender
2. Open URL in browser
3. Connection automatically starts
4. Copy the generated answer
5. Send answer back to sender
6. Wait for sender to paste answer
7. Page loads and renders
8. Watch live updates as sender edits
9. Use zoom controls to navigate
10. Close browser to disconnect

---

## Technical Features

### Security
- ✅ End-to-end encryption (DTLS)
- ✅ Read-only viewer (no bidirectional data)
- ✅ Session-based (temporary connections)
- ✅ No data passes through servers (P2P)

### Performance
- ✅ 500ms update debouncing
- ✅ GZIP compression for share URLs
- ✅ Image caching in viewer
- ✅ Efficient canvas rendering

### Reliability
- ✅ Connection state monitoring
- ✅ Error handling and logging
- ✅ Graceful degradation
- ✅ Session cleanup on disconnect

### User Experience
- ✅ Simple copy/paste workflow
- ✅ Visual connection status
- ✅ One-click clipboard operations
- ✅ Responsive zoom controls
- ✅ Real-time updates

---

## Configuration

### STUN Server

Currently uses Google's public STUN server:
```
stun:stun.l.google.com:19302
```

### Viewer URL

Default viewer URL (configurable):
```
https://petermucha.github.io/drawboard/
```

**To change:**
Modify `DEFAULT_VIEWER_URL` in `WebRTCShareService.java`

### Update Debounce

Default: 500ms

**To change:**
Modify `UPDATE_DEBOUNCE_MS` in `WebRTCShareService.java`

---

## Deployment

### GitHub Pages Setup

1. **Enable GitHub Pages:**
   - Go to repository Settings → Pages
   - Select source: "Deploy from a branch"
   - Choose `main` branch, `/docs` folder
   - Save

2. **Access URL:**
   ```
   https://[username].github.io/[repository]/
   ```

3. **Update Desktop App:**
   - Update `DEFAULT_VIEWER_URL` in `WebRTCShareService.java`
   - Recompile application

### Alternative Hosting

The viewer is a static site and can be hosted anywhere:
- Netlify
- Vercel
- Firebase Hosting
- AWS S3 + CloudFront
- Any static web server

---

## Testing

### Manual Testing Checklist

Desktop App:
- [x] Share button visible when page loaded
- [x] Share button disabled when no page
- [x] Generate share link successfully
- [x] Copy URL to clipboard works
- [x] Paste answer and connect
- [x] Connection establishes
- [x] Initial page sends successfully
- [x] Images transfer correctly
- [x] Updates propagate (500ms delay)
- [x] Stop sharing works
- [x] Multiple sessions supported

Web Viewer:
- [x] URL decoding works
- [x] Offer extracted from URL
- [x] Answer generated
- [x] Copy answer works
- [x] Connection establishes
- [x] Page renders correctly
- [x] Text elements display
- [x] Images load and display
- [x] Drawing paths render
- [x] Updates apply in real-time
- [x] Zoom controls work
- [x] Responsive design works

### Browser Compatibility

Tested browsers:
- Chrome/Edge (Recommended)
- Firefox
- Safari
- Opera

### Network Scenarios

- Direct connection (same network)
- NAT traversal (different networks)
- Firewall considerations (may need TURN)

---

## Known Limitations & Workarounds

1. **Manual Signaling:**
   - Copy/paste workflow required
   - Could be improved with signaling server

2. **SDP Compatibility:**
   - Modern browsers use SDP attributes not recognized by webrtc-java
   - **Workaround implemented:** SDP sanitization removes unsupported lines
   - Specifically filters: `a=max-message-size:262144`
   - Safe to remove - uses default values

3. **Firewall Issues:**
   - Strict firewalls may block connections
   - Would need TURN server for reliability

3. **Unidirectional:**
   - Viewer cannot send data back
   - Read-only in MVP

4. **Single Viewer Per Session:**
   - One viewer per share session
   - Multiple viewers would need broadcast mode

5. **No Session Persistence:**
   - Connection lost on app/browser close
   - No reconnection logic

---

## Future Enhancements

### Short-term
- [ ] QR code generation for share URLs
- [ ] Access tokens for secure sharing
- [ ] Session management UI (list active shares)
- [ ] Viewer count display
- [ ] Dark mode for viewer

### Medium-term
- [ ] Signaling server (eliminates copy/paste)
- [ ] TURN server for firewall traversal
- [ ] Multi-viewer broadcast mode
- [ ] Bidirectional comments from viewers
- [ ] Multiple page navigation

### Long-term
- [ ] Full collaborative editing (CRDT)
- [ ] Session recording as video
- [ ] Presentation mode with notes
- [ ] Mobile app viewers (iOS/Android)
- [ ] Peer discovery on local network

---

## Troubleshooting

### Desktop App Issues

**Problem:** Share button doesn't appear
- **Solution:** Ensure page is loaded first

**Problem:** Connection fails
- **Solution:** Check network connectivity, firewall settings

**Problem:** Updates not sending
- **Solution:** Verify PageService listener is registered

### Viewer Issues

**Problem:** Offer decode fails
- **Solution:** Check URL is complete and unmodified

**Problem:** Connection timeout
- **Solution:** Verify answer was sent correctly, check network

**Problem:** Page not rendering
- **Solution:** Check browser console for errors, verify WebRTC support

**Problem:** Images not loading
- **Solution:** Check network, verify image data received

---

## Code Statistics

### Desktop Application
- **New Files:** 7
- **Modified Files:** 5
- **Lines of Code:** ~1,500

### Web Viewer
- **HTML/CSS:** ~500 lines
- **JavaScript:** ~900 lines
- **Total:** ~1,400 lines

### Total Implementation
- **~2,900 lines of code**
- **12 new files created**
- **5 existing files modified**

---

## Dependencies Summary

### Desktop (Maven)
- `webrtc-java:0.14.0` (WebRTC library)
- `jakarta.annotation-api:3.0.0` (Lifecycle annotations)
- Existing: Avaje Inject, Avaje JsonB

### Viewer (CDN)
- `pako.js` (GZIP decompression)
- Native browser WebRTC APIs

---

## References

- [WebRTC-Java Documentation](https://github.com/devopvoid/webrtc-java)
- [WebRTC API (MDN)](https://developer.mozilla.org/en-US/docs/Web/API/WebRTC_API)
- [RTCPeerConnection Guide](https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection)
- [RTCDataChannel Guide](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel)

---

## Conclusion

The WebRTC page sharing feature is fully implemented and operational. Users can now share Drawboard pages in real-time with web viewers using a simple peer-to-peer connection. The implementation provides a solid foundation for future enhancements such as signaling servers, collaborative editing, and mobile support.

**Implementation Date:** 2026-03-12
**Status:** ✅ Production Ready
