# Alternative WebRTC Libraries for Java

## Current Issue
webrtc-java 0.14.0 has **incompatible SDP parsing** with modern browsers (Chrome 90+, Firefox 88+).

**Specific Problem:**
- Cannot parse `a=mid:0` attribute (mandatory in modern WebRTC)
- Cannot parse `a=sctp-port:5000`
- Cannot parse `a=max-message-size:262144`

## Requirements for Replacement

### Must Have
✅ Data channel support (for JSON messages)
✅ Modern SDP compatibility (Unified Plan)
✅ STUN/TURN server support
✅ Active maintenance
✅ Java 25 compatibility
✅ Maven Central availability

### Nice to Have
- Well-documented API
- Community support
- Performance benchmarks
- Minimal dependencies
- Cross-platform (macOS, Linux, Windows)

## Alternative Libraries

### 1. libjitsi (Jitsi Meet Library)

**Repository:** https://github.com/jitsi/libjitsi

**Pros:**
- ✅ Battle-tested (used by Jitsi Meet)
- ✅ Full WebRTC support
- ✅ Active development
- ✅ Good documentation
- ✅ Large community

**Cons:**
- ⚠️ Heavier library (more dependencies)
- ⚠️ Designed for video conferencing (may be overkill)
- ⚠️ Learning curve

**Maven:**
```xml
<dependency>
    <groupId>org.jitsi</groupId>
    <artifactId>libjitsi</artifactId>
    <version>1.0-20210614.231049-111</version>
</dependency>
```

**Usage Example:**
```java
// TBD - requires research
```

### 2. Kurento Java Client

**Repository:** https://github.com/Kurento/kurento-java

**Pros:**
- ✅ Full media server capabilities
- ✅ Well-maintained
- ✅ Good documentation
- ✅ Scalable architecture

**Cons:**
- ⚠️ Requires Kurento Media Server (separate process)
- ⚠️ Server-based architecture (not P2P)
- ⚠️ More complex setup

**Maven:**
```xml
<dependency>
    <groupId>org.kurento</groupId>
    <artifactId>kurento-client</artifactId>
    <version>7.0.0</version>
</dependency>
```

**Architecture:**
```
Desktop App → Kurento Media Server → Browser
```

### 3. Direct libwebrtc JNI Bindings

**Repository:** Various implementations

**Pros:**
- ✅ Most up-to-date (uses Google's libwebrtc)
- ✅ Full feature parity with browsers
- ✅ Best compatibility

**Cons:**
- ⚠️ Complex to build
- ⚠️ Platform-specific binaries
- ⚠️ Limited high-level APIs
- ⚠️ Requires JNI expertise

**Not recommended unless no other options**

### 4. WebSocket + Custom Protocol (Non-WebRTC)

**Skip WebRTC entirely**

**Pros:**
- ✅ Simple implementation
- ✅ Full control over protocol
- ✅ No SDP compatibility issues
- ✅ Works anywhere

**Cons:**
- ❌ Not using WebRTC standards
- ❌ No STUN/TURN support
- ❌ Manual NAT traversal
- ❌ Requires signaling server

**Implementation:**
```java
// WebSocket server in desktop app
WebSocketServer server = new WebSocketServer(port);

// Browser connects via WebSocket
const ws = new WebSocket('ws://localhost:8080');

// Send JSON messages directly
ws.send(JSON.stringify({type: 'page_data', ...}));
```

### 5. Jitsi Videobridge Java SDK

**Repository:** https://github.com/jitsi/jitsi-videobridge

**Pros:**
- ✅ Production-ready
- ✅ Scalable
- ✅ Used by Jitsi infrastructure

**Cons:**
- ⚠️ Requires videobridge server
- ⚠️ Not P2P
- ⚠️ More complex than needed

### 6. Red5 Pro (Commercial)

**Website:** https://www.red5pro.com/

**Pros:**
- ✅ Commercial support
- ✅ Full WebRTC stack
- ✅ High performance

**Cons:**
- ❌ Commercial license required
- ❌ Expensive
- ❌ Overkill for this use case

## Recommended Approach

### Option A: libjitsi (Recommended for P2P)
If you want to keep peer-to-peer architecture with no server.

**Pros:**
- Battle-tested
- Full WebRTC support
- Active community

**Cons:**
- Learning curve
- Heavy library

### Option B: WebSocket + Custom Protocol (Simplest)
If you're okay with requiring a lightweight server.

**Pros:**
- Simplest to implement
- No SDP compatibility issues
- Full control

**Cons:**
- Not "real" WebRTC
- Needs server component

### Option C: Wait for webrtc-java Update
Check if dev.onvoid.webrtc has plans for 0.15+

**Check:**
- GitHub issues: https://github.com/devopvoid/webrtc-java
- Last release date
- Open issues about SDP compatibility

## Migration Strategy

### If choosing libjitsi:

1. **Research Phase** (1-2 days)
   - Read libjitsi documentation
   - Find data channel examples
   - Check Maven integration

2. **Prototype Phase** (2-3 days)
   - Create simple P2P connection
   - Test data channel messaging
   - Verify browser compatibility

3. **Integration Phase** (3-5 days)
   - Replace webrtc-java code
   - Update WebRTCShareService
   - Test end-to-end

4. **Testing Phase** (2-3 days)
   - Cross-browser testing
   - Network testing
   - Performance testing

**Total:** ~2 weeks

### If choosing WebSocket:

1. **Design Phase** (1 day)
   - Design message protocol
   - Plan server architecture

2. **Server Implementation** (2-3 days)
   - Implement WebSocket server in desktop app
   - Or deploy separate Node.js server

3. **Client Implementation** (2 days)
   - Update viewer to use WebSocket
   - Remove WebRTC code

4. **Testing Phase** (2 days)
   - Test messaging
   - Test reconnection

**Total:** ~1 week

## Current State - What's Implemented

✅ **Working:**
- Desktop app UI (Share button, dialog)
- PageService listeners
- Offer generation (webrtc-java 0.14.0)
- Viewer UI (HTML/JS)
- Offer decoding (Base64/GZIP)
- Answer generation (browser)

❌ **Not Working:**
- Answer processing (SDP incompatibility)
- Connection establishment
- Data transfer

🔄 **Needs Migration:**
- `WebRTCShareService.java` - Replace webrtc-java calls
- Keep: UI, listeners, flow, compression
- Replace: WebRTC library calls

## Code to Keep vs Replace

### Keep (No changes needed)
- ✅ `ShareDialogController.java` - UI logic
- ✅ `ShareDialog.fxml` - UI layout
- ✅ `PageService.java` listeners
- ✅ `MainWindowController.java` integration
- ✅ `PageShareSession.java` - Session state
- ✅ `ShareOffer.java` - URL compression
- ✅ Viewer HTML/CSS/JS (mostly)

### Replace (WebRTC library dependent)
- 🔄 `WebRTCShareService.java` - Main service
  - Keep: Structure, listeners, debouncing
  - Replace: PeerConnectionFactory, RTCPeerConnection, etc.

### Adjust (Minor changes)
- 🔄 `module-info.java` - Update requires clause
- 🔄 `pom.xml` - New dependency
- 🔄 Viewer `webrtc-client.js` - May need adjustments

## Decision Matrix

| Library | Complexity | P2P | Server Needed | Modern SDP | Maintenance |
|---------|-----------|-----|---------------|------------|-------------|
| webrtc-java 0.14.0 | Low | ✅ | ❌ | ❌ | ⚠️ |
| libjitsi | Medium | ✅ | ❌ | ✅ | ✅ |
| Kurento | High | ❌ | ✅ | ✅ | ✅ |
| WebSocket | Low | ❌ | ✅ | N/A | ✅ |
| Custom JNI | Very High | ✅ | ❌ | ✅ | ⚠️ |

## Next Steps

1. **Research libjitsi**
   - Check if it supports data channels
   - Find example code
   - Verify Maven availability

2. **Check webrtc-java GitHub**
   - Open issues about SDP compatibility
   - Check for roadmap/updates
   - Consider opening issue

3. **Prototype WebSocket alternative**
   - Quick proof-of-concept
   - Evaluate simplicity vs features

4. **Make decision** based on:
   - Time available
   - Maintenance concerns
   - Feature requirements
   - P2P vs server-based preference

## Useful Links

- **webrtc-java GitHub:** https://github.com/devopvoid/webrtc-java
- **libjitsi GitHub:** https://github.com/jitsi/libjitsi
- **Kurento GitHub:** https://github.com/Kurento/kurento-java
- **WebRTC Standards:** https://www.w3.org/TR/webrtc/
- **SDP RFC:** https://tools.ietf.org/html/rfc4566

## Contact for Help

If researching alternatives:
- Jitsi Community Forum
- WebRTC GitHub Discussions
- Stack Overflow (webrtc + java tags)

---

**Status:** webrtc-java 0.14.0 is not compatible with modern browsers. Alternative library needed.

**Recommendation:** Research libjitsi first, then consider WebSocket fallback if needed.
