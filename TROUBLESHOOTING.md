# WebRTC Troubleshooting Guide

## Common Issues and Solutions

### Issue 1: "Failed to execute 'atob' on 'Window': The string to be decoded is not correctly encoded"

**Problem:**
The JavaScript decoder fails when trying to decode the offer from the URL.

**Root Cause:**
Java uses URL-safe Base64 encoding (with `-` and `_` characters), but JavaScript's `atob()` function only accepts standard Base64 (with `+` and `/` characters).

**Solution:**
Updated `offer-decoder.js` to convert URL-safe Base64 to standard Base64 before decoding:

```javascript
// Convert URL-safe Base64 to standard Base64
let standardBase64 = urlDecoded.replace(/-/g, '+').replace(/_/g, '/');

// Add padding if necessary
while (standardBase64.length % 4) {
    standardBase64 += '=';
}

// Now atob() will work
const base64Decoded = atob(standardBase64);
```

**Status:** ✅ Fixed in commit

---

### Issue 2: "Controller value already specified" in ShareDialog

**Problem:**
ShareDialog fails to load with `javafx.fxml.LoadException: Controller value already specified`.

**Root Cause:**
The FXML file specified `fx:controller="com.drawboard.ui.ShareDialogController"`, but the code was also setting the controller programmatically via `loader.setController(this)`. JavaFX doesn't allow both.

**Solution:**
Removed the `fx:controller` attribute from `ShareDialog.fxml` to allow full programmatic control with constructor parameters.

**Before:**
```xml
<VBox xmlns="http://javafx.com/javafx"
      xmlns:fx="http://javafx.com/fxml"
      fx:controller="com.drawboard.ui.ShareDialogController"
      ...>
```

**After:**
```xml
<VBox xmlns="http://javafx.com/javafx"
      xmlns:fx="http://javafx.com/fxml"
      ...>
```

**Status:** ✅ Fixed in commit

---

### Issue 3: "Invalid SDP line" when processing answer

**Problem:**
Desktop app crashes with error: `Create session description failed: Invalid SDP line. [a=max-message-size:262144]`

**Root Cause:**
Modern browsers (Chrome, Firefox) include the `a=max-message-size:262144` attribute in their SDP answers, but webrtc-java 0.14.0 doesn't recognize this attribute and rejects the entire SDP.

**Solution:**
Added SDP sanitization in `WebRTCShareService.processAnswer()` to filter out unsupported attributes before passing to the WebRTC library:

```java
private String sanitizeSdp(String sdp) {
    StringBuilder sanitized = new StringBuilder();
    String[] lines = sdp.split("\r?\n");

    for (String line : lines) {
        // Skip unsupported SDP attributes
        if (line.startsWith("a=max-message-size:")) {
            log.debug("Removing unsupported SDP line: {}", line);
            continue;
        }
        sanitized.append(line).append("\r\n");
    }

    return sanitized.toString().trim();
}
```

**Impact:**
- The `max-message-size` attribute is a hint for the maximum data channel message size
- Removing it is safe - the WebRTC implementation will use its default value
- No loss of functionality

**Status:** ✅ Fixed in commit

---

### Issue 4: Empty or Missing Offer Parameter

**Problem:**
URL has `?offer=` but the parameter is empty or very short.

**Root Cause:**
- Share URL was copied incompletely
- WebRTC offer generation failed in desktop app
- URL was truncated during copy/paste

**Solution:**
1. **Desktop app:** Added validation to ensure offer SDP is not null/empty
2. **Web viewer:** Added better error messages and console logging
3. **Created test page:** `test-decoder.html` to verify encoding/decoding

**Prevention:**
- Always copy the complete share URL (check that it's very long, 500+ chars)
- Check desktop app logs for WebRTC errors
- Use the test decoder page to verify the URL before sending

**Status:** ✅ Fixed with better validation

---

## Testing Tools

### Test Decoder Page

Located at: `docs/test-decoder.html`

**Features:**
1. **Base64 Conversion Test:** Verify URL-safe to standard Base64 conversion
2. **Full Offer Decoding:** Test complete URL decoding with real or test offers
3. **Test Offer Generation:** Create test offers from sample SDP

**Usage:**
```bash
# Open test page locally
open docs/test-decoder.html

# Or via server
python3 -m http.server 8000
# Then open: http://localhost:8000/test-decoder.html
```

---

## Debugging Checklist

### Desktop App Issues

1. **Share button not appearing:**
   - ✅ Is a page loaded?
   - ✅ Check `MainWindowController.loadPage()` is called
   - ✅ Verify `btnSharePage` is initialized

2. **Share URL generation fails:**
   - ✅ Check logs: `grep -i webrtc ~/.drawboard/logs/app.log`
   - ✅ Verify WebRTC library loaded: Look for "WebRTC peer connection factory initialized"
   - ✅ Check for exceptions in `WebRTCShareService.createShareOffer()`

3. **Connection never establishes:**
   - ✅ Verify answer was pasted correctly (should be very long)
   - ✅ Check for firewall blocking WebRTC
   - ✅ Look for ICE candidate errors in logs

### Web Viewer Issues

1. **"No offer found in URL":**
   - ✅ URL must contain `?offer=...`
   - ✅ Verify complete URL was copied
   - ✅ Check URL in browser address bar

2. **"Failed to decode offer from URL":**
   - ✅ Open browser DevTools (F12) → Console
   - ✅ Look for specific error (atob, pako, etc.)
   - ✅ Verify pako.js loaded from CDN
   - ✅ Test with `test-decoder.html`

3. **Answer generated but connection fails:**
   - ✅ Verify answer was sent back to desktop app
   - ✅ Check desktop app pasted answer and clicked "Connect"
   - ✅ Look for WebRTC errors in browser console
   - ✅ Check if STUN server is accessible

4. **Page loads but updates don't appear:**
   - ✅ Check connection status (should show "Connected")
   - ✅ Look for data channel state in console
   - ✅ Verify desktop app shows "sharing page in real-time"
   - ✅ Wait at least 1 second after edit (500ms debounce)

---

## Network Requirements

### Required Ports/Protocols

- **WebRTC Media:** UDP ports (typically 49152-65535)
- **STUN Server:** UDP port 19302 (stun.l.google.com)
- **HTTPS:** Port 443 (for GitHub Pages viewer)

### Firewall Considerations

**Desktop App:**
- Needs outbound UDP access
- Needs access to STUN server
- May need TURN server for strict firewalls

**Web Viewer:**
- Needs WebRTC support (all modern browsers)
- May be blocked on corporate networks
- VPN can interfere with connection

### Testing Network Issues

1. **Check STUN server access:**
   ```bash
   # Test UDP connectivity to STUN server
   nc -u stun.l.google.com 19302
   ```

2. **Test in different network:**
   - Try mobile hotspot
   - Try different WiFi network
   - Try with/without VPN

3. **Browser WebRTC check:**
   Visit: https://test.webrtc.org/

---

## Logs and Diagnostics

### Desktop App Logs

**Location:** `~/.drawboard/logs/app.log`

**Key messages to look for:**
```
INFO  WebRTC share service initialized
INFO  Created share offer for page [id] (session: [sessionId]), URL length: [chars]
INFO  Connection state changed: CONNECTED
DEBUG Data channel state: OPEN
DEBUG Sent page update for page [id]
ERROR Failed to create share offer
ERROR Failed to process answer
```

**Enable debug logging:**
Edit `logback.xml` and set WebRTC logger to DEBUG:
```xml
<logger name="com.drawboard.service.WebRTCShareService" level="DEBUG"/>
```

### Browser Console

**Access:** Press F12 → Console tab

**Key messages to look for:**
```
Successfully decoded offer from URL
ICE candidate: [...]
Data channel received: page-data
Data channel opened
Received message: page_data
Received image: [filename]
```

**Common errors:**
```
Failed to decode offer from URL: [error]
InvalidCharacterError: Failed to execute 'atob'
RTCPeerConnection error: [error]
Data channel error: [error]
```

---

## Known Limitations

1. **Manual Signaling:**
   - Requires copy/paste of offer and answer
   - Potential for user error
   - Future: Implement signaling server

2. **Firewall Issues:**
   - May not work behind strict firewalls
   - Requires UDP access for WebRTC
   - Future: Add TURN server support

3. **Single Viewer:**
   - One viewer per share session
   - Multiple viewers require multiple shares
   - Future: Implement broadcast mode

4. **No Reconnection:**
   - Closing app/browser ends session
   - No automatic reconnection
   - Future: Add reconnection logic

5. **Large Images:**
   - Transferred as base64 (increases size ~33%)
   - May take time on slow connections
   - Future: Optimize image transfer

---

## Performance Tips

### For Desktop App Users

1. **Keep pages moderate in size:**
   - < 50 elements for best performance
   - Large images increase initial load time
   - Consider splitting very large pages

2. **Stable network connection:**
   - Use wired connection if possible
   - Avoid mobile hotspots for large pages
   - Keep app running while sharing

3. **Monitor system resources:**
   - WebRTC uses CPU for encoding
   - Multiple shares increase load
   - Close unused shares

### For Viewers

1. **Use modern browser:**
   - Chrome/Edge recommended (best WebRTC support)
   - Keep browser updated
   - Clear cache if issues persist

2. **Stable network:**
   - Reliable connection important for real-time updates
   - Avoid switching networks mid-session
   - Refresh page to reconnect

3. **Device performance:**
   - Canvas rendering is CPU-intensive
   - Close other tabs if slow
   - Desktop preferred over mobile for large pages

---

## Getting Help

### Before Asking for Help

1. Check this troubleshooting guide
2. Review application logs
3. Test with `test-decoder.html`
4. Try on different network/device
5. Check browser compatibility

### Information to Provide

When reporting issues, include:
- Desktop app version
- Browser and version
- Operating system
- Complete error message
- Steps to reproduce
- Logs (sanitize sensitive info)
- Share URL length (not the actual URL)

### Resources

- **Documentation:** See `WEBRTC_IMPLEMENTATION.md`
- **Quick Start:** See `docs/QUICK_START.md`
- **Test Tools:** See `docs/test-decoder.html`
- **Issue Tracker:** [GitHub Issues]

---

Last Updated: 2026-03-12
