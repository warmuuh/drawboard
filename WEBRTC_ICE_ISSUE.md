# WebRTC ICE Candidate Exchange Issue

## Current Problem

```
12:59:30 Peer connection state changed: CONNECTING
12:59:45 Peer connection state changed: FAILED
```

Connection fails after 15 seconds of trying to connect.

## Root Cause

**Manual signaling doesn't support ICE candidate exchange.**

### What's Happening

1. ✅ Desktop creates offer with SDP
2. ✅ Browser receives offer, creates answer with SDP + ICE candidates
3. ✅ Desktop receives answer with browser's ICE candidates
4. ❌ **Desktop's ICE candidates never reach the browser**
5. ❌ Connection fails - browser doesn't know how to reach desktop

### Why ICE Candidates Matter

ICE (Interactive Connectivity Establishment) candidates are network addresses where each peer can be reached:

- **Host candidate**: Local IP (e.g., 192.168.1.100)
- **Server reflexive (srflx)**: Public IP via STUN (e.g., 130.41.86.78)
- **Relay (relay)**: Via TURN server (if configured)

Both peers need ALL of each other's candidates to find a working connection path.

### The Manual Signaling Problem

With real signaling servers, ICE candidates are sent as they're generated:
```
Desktop → Server: Here's candidate 1
Desktop → Server: Here's candidate 2
Server → Browser: Desktop sent candidate 1
Server → Browser: Desktop sent candidate 2
```

With manual copy/paste:
```
Desktop: Here's my offer (no candidates yet, they generate later)
User copies and pastes offer
Browser: Here's my answer + all my candidates (browser waited)
User copies and pastes answer
Desktop: Got browser's candidates, but browser doesn't have mine!
Connection FAILS
```

## Solutions

### Option 1: Use Signaling Server (RECOMMENDED)

Implement a WebSocket signaling server that relays ICE candidates.

**Pros:**
- ✅ Proper WebRTC implementation
- ✅ Better UX (no copy/paste)
- ✅ Supports trickle ICE
- ✅ Production-ready approach

**Cons:**
- ⚠️ Requires server infrastructure
- ⚠️ More complex architecture

**Implementation time:** 2-3 days

### Option 2: Include ICE Candidates in SDP (WORKAROUND)

Wait longer for ICE gathering on both sides, embed candidates in SDP.

**Current wait time:** 2 seconds
**May need:** 5-10 seconds

**Issue:**
- webrtc-java may not update the local description with candidates
- `getLocalDescription()` might return original SDP without candidates

**To try:**
Increase wait time from 2s to 10s and check if offer includes `a=candidate:` lines.

### Option 3: Host-Only Mode (WORKAROUND)

Force both peers to use only host candidates (local network only).

**Pros:**
- ✅ Works on same network
- ✅ No STUN/TURN needed

**Cons:**
- ❌ Only works on LAN
- ❌ Won't work over internet
- ❌ Limited usefulness

### Option 4: Switch to Different Library

As discussed, webrtc-java 0.14.0 has limitations.

**Better alternatives:**
- libjitsi
- Custom WebSocket protocol
- Different WebRTC implementation

## What to Check Now

### 1. Check Desktop Offer

Look at the share URL you generate. Decode it and check if it includes `a=candidate:` lines.

**Expected:**
```
v=0
o=...
...
a=candidate:... (should have several of these)
a=candidate:...
```

**If NO candidates in offer:**
- Desktop's ICE gathering isn't working
- Or getLocalDescription() doesn't return updated SDP
- Or 2 seconds isn't enough

### 2. Check Desktop Logs

Look for:
```
INFO  ICE gathering complete - offer includes X candidates
```

**If X = 0:**
- ICE gathering failed
- Need to debug why candidates aren't being generated

### 3. Check Network

Desktop app needs to generate candidates:
- **Host:** Desktop's local IP
- **srflx:** Desktop's public IP (via STUN)

**If behind corporate firewall:**
- STUN might be blocked
- UDP might be blocked
- May need TURN server

## Immediate Actions

### Action 1: Check Offer Contents

1. Generate new share
2. Copy the URL
3. Paste into `docs/test-decoder.html`
4. Decode and check for `a=candidate:` lines

**If present:** Desktop is generating candidates
**If absent:** ICE gathering issue on desktop

### Action 2: Increase Wait Time

Edit `WebRTCShareService.java`:
```java
// Change from 2 seconds to 10 seconds
scheduler.schedule(() -> {
    // ...
}, 10, TimeUnit.SECONDS);  // ← Change to 10
```

### Action 3: Add ICE Gathering Listener

Instead of waiting arbitrary time, listen for gathering state:

```java
// This would be better but requires API support
peerConnection.onIceGatheringStateChange = (state) -> {
    if (state == RTCIceGatheringState.COMPLETE) {
        // Now get the updated description
    }
};
```

**Problem:** webrtc-java might not support this callback properly.

## Recommended Next Steps

1. **Short-term:** Check if offer includes candidates
   - If yes: Might work with longer wait
   - If no: webrtc-java ICE issue

2. **Medium-term:** Implement simple signaling server
   - Node.js WebSocket server
   - 200 lines of code
   - Solves ICE exchange problem

3. **Long-term:** Switch WebRTC library
   - Research libjitsi
   - Or use WebSocket instead

## Simple Signaling Server Example

```javascript
// server.js - Run with: node server.js
const WebSocket = require('ws');
const wss = new WebSocket.Server({ port: 8080 });

const sessions = new Map();

wss.on('connection', (ws) => {
    ws.on('message', (message) => {
        const msg = JSON.parse(message);

        if (msg.type === 'offer') {
            sessions.set(msg.sessionId, { desktop: ws });
            // Wait for viewer to connect
        } else if (msg.type === 'answer') {
            const session = sessions.get(msg.sessionId);
            if (session && session.desktop) {
                session.desktop.send(JSON.stringify(msg));
            }
        } else if (msg.type === 'ice-candidate') {
            // Relay ICE candidates
            const session = sessions.get(msg.sessionId);
            if (session) {
                const target = msg.from === 'desktop' ? session.viewer : session.desktop;
                if (target) target.send(JSON.stringify(msg));
            }
        }
    });
});
```

This solves the ICE exchange problem properly.

## Current Status

❌ **Connection fails due to incomplete ICE candidate exchange**

**What works:**
- SDP offer/answer exchange
- Line ending normalization
- Attribute sanitization

**What doesn't work:**
- ICE candidate exchange (manual signaling limitation)
- Actual connection establishment

**Bottom line:** Manual signaling is fundamentally limited. Need either:
- Signaling server (proper solution)
- Or different approach entirely (WebSocket instead of WebRTC)

---

**Recommendation:** Implement a simple signaling server. It's the standard WebRTC approach and will solve all these issues.
