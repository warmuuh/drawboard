# WebRTC Compatibility Issue - webrtc-java 0.14.0

## Problem Summary

webrtc-java 0.14.0 has **limited SDP compatibility** with modern browsers (Chrome 90+, Firefox 88+, Edge 90+).

### Symptoms
```
Exception: Create session description failed: Invalid SDP line. [a=mid:0]
```

### Root Cause

Modern browsers use **Unified Plan SDP** format (RFC 8829), while webrtc-java 0.14.0 appears to expect an older SDP format or has incomplete support for data channel SDP attributes.

**Problematic Attributes:**
- ❌ `a=max-message-size:262144` - Can be filtered (optional)
- ❌ `a=sctp-port:5000` - Can be filtered (optional)
- ❌ `a=mid:0` - **CANNOT be filtered** (mandatory for media stream identification)

## Why `a=mid:` Cannot Be Removed

The `mid` (media stream identification) attribute is **mandatory** in WebRTC:
- Required by RFC 8829 (Unified Plan)
- Identifies which m-line a particular attribute applies to
- Essential for BUNDLE negotiation
- Removing it breaks the SDP structure

## Attempted Solutions

### ✅ What Worked
1. Filtering optional attributes (`max-message-size`, `sctp-port`)
2. Base64 encoding fixes
3. SDP sanitization framework

### ❌ What Didn't Work
1. Removing `a=mid:` - breaks SDP structure
2. Setting `RTCSdpSemantics.UNIFIED_PLAN` - property doesn't exist in 0.14.0
3. Setting `offerToReceiveAudio/Video` - properties don't exist in 0.14.0

## Possible Solutions

### Option 1: Upgrade webrtc-java (RECOMMENDED)

**Check for newer version:**
```bash
# Check if there's a newer version
mvn versions:display-dependency-updates | grep webrtc
```

**Upgrade if available:**
```xml
<dependency>
    <groupId>dev.onvoid.webrtc</groupId>
    <artifactId>webrtc-java</artifactId>
    <version>0.15.0</version> <!-- or latest -->
</dependency>
```

**Pros:**
- ✅ Full modern WebRTC support
- ✅ No workarounds needed
- ✅ Better browser compatibility

**Cons:**
- ⚠️ May have breaking API changes
- ⚠️ May not exist (0.14.0 might be latest)

### Option 2: Reverse the Role (WORKAROUND)

Instead of desktop creating offer:
1. **Browser creates offer**
2. Desktop creates answer

**Changes needed:**
- Browser generates offer, shows it to user
- User pastes offer into desktop app
- Desktop generates answer
- User pastes answer back to browser

**Implementation:**
```javascript
// In viewer: create offer instead of answer
const offer = await peerConnection.createOffer();
await peerConnection.setLocalDescription(offer);
// Show offer.sdp to user to paste in desktop app
```

```java
// In desktop: create answer instead of offer
public ShareAnswer createShareAnswer(String browserOfferSdp) {
    RTCSessionDescription offer = new RTCSessionDescription(RTCSdpType.OFFER, browserOfferSdp);
    peerConnection.setRemoteDescription(offer, ...);

    peerConnection.createAnswer(answerOptions, new CreateSessionDescriptionObserver() {
        // Generate answer SDP
    });
}
```

**Pros:**
- ✅ Desktop app (older library) processes browser's offer
- ✅ Desktop generates simpler answer
- ✅ May have better compatibility

**Cons:**
- ⚠️ Reverses the flow (confusing UX)
- ⚠️ Browser offer might still have unsupported attributes
- ⚠️ More complex implementation

### Option 3: Use Different WebRTC Library

Switch from webrtc-java to alternatives:

**Alternatives:**
1. **libjitsi** - More mature, used by Jitsi
2. **Kurento** - Media server approach
3. **JNI wrapper** - Direct libwebrtc bindings
4. **WebSocket + custom protocol** - Skip WebRTC entirely

**Pros:**
- ✅ Better compatibility
- ✅ More features

**Cons:**
- ⚠️ Major refactoring required
- ⚠️ Different APIs
- ⚠️ Learning curve

### Option 4: Use Signaling Server (MODERN APPROACH)

Instead of manual SDP exchange:
1. Implement signaling server (WebSocket)
2. Both peers connect to server
3. Server relays SDP/ICE candidates
4. No manual copy/paste

**Pros:**
- ✅ Better UX (no copy/paste)
- ✅ Can transform SDP on server
- ✅ Can log/debug SDP exchanges
- ✅ Industry standard approach

**Cons:**
- ⚠️ Requires server infrastructure
- ⚠️ More complex architecture
- ⚠️ Doesn't solve core compatibility issue

### Option 5: SDP Munging (HACKY)

Attempt to transform browser's modern SDP into format webrtc-java expects:

```java
private String mungeSdpForLegacyLibrary(String modernSdp) {
    // Try to convert Unified Plan to Plan B
    // Remove/transform unsupported attributes
    // Simplify media descriptions
    // ...very fragile
}
```

**Pros:**
- ✅ Keeps current architecture
- ✅ No library changes

**Cons:**
- ❌ Very fragile
- ❌ Hard to maintain
- ❌ May break with browser updates
- ❌ Not recommended

## Recommended Path Forward

### Short-term (Testing/POC)
1. Check if webrtc-java has newer version
2. Try Option 2 (reverse roles) as quick test
3. Document exact SDP differences

### Long-term (Production)
1. **Upgrade to newer webrtc-java** (if available)
2. **Or switch to more mature library** (libjitsi)
3. **Implement signaling server** (proper WebRTC architecture)
4. **Consider alternative**: WebSocket + custom data protocol

## Testing Current State

Run desktop app with debug logging:
```bash
sdk env && mvn javafx:run
```

Check logs for:
```
DEBUG Full offer SDP:
[...see what SDP format desktop generates...]

DEBUG Sanitized SDP content:
[...see what remains after filtering...]
```

Compare desktop's offer format vs browser's answer format to understand compatibility gap.

## Browser SDP Example

Modern browser answer for data channel:
```sdp
v=0
o=- 8857715508147634496 2 IN IP4 127.0.0.1
s=-
t=0 0
a=group:BUNDLE 0
a=extmap-allow-mixed
a=msid-semantic: WMS
m=application 9 UDP/DTLS/SCTP webrtc-datachannel
c=IN IP4 0.0.0.0
a=ice-ufrag:ixVl
a=ice-pwd:sqJWMf6pLKzXyn2M/MA3GXjT
a=ice-options:trickle
a=fingerprint:sha-256 87:C5:...
a=setup:active
a=mid:0                    ← REQUIRED, can't remove
a=sctp-port:5000          ← Optional, can remove
a=max-message-size:262144 ← Optional, can remove
```

## Workaround Implementation Priority

1. **First**: Try upgrading webrtc-java
2. **Second**: Test with reversed roles (browser creates offer)
3. **Third**: Evaluate alternative libraries
4. **Fourth**: Consider signaling server approach
5. **Last Resort**: Custom WebSocket protocol (skip WebRTC)

## Current Status

- webrtc-java 0.14.0: ❌ Incompatible with modern browser SDP
- Offer generation: ✅ Works
- Answer processing: ❌ Fails on `a=mid:0`
- Alternative approaches: ⏳ To be tested

## Next Steps

1. Research if webrtc-java 0.15+ exists
2. Test Option 2 (reversed roles)
3. Investigate libjitsi compatibility
4. Consider signaling server architecture

---

**Update:** This is a known limitation of using webrtc-java 0.14.0 with modern browsers. A more modern WebRTC library or reversed connection flow may be required for production use.
