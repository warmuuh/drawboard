# Debug WebRTC Connection

## You Are Here

✅ Desktop app processes answer successfully
✅ Remote description set
⏳ Waiting for connection to establish

## What to Check Now

### 1. Desktop App Logs

Look for these messages in order:

```
✅ Created share offer for page [...] (session: [...])
✅ Remote description set successfully for session [...]
⏳ Peer connection state changed to: CONNECTING
⏳ ICE candidate generated: [...]
⏳ Peer connection state changed to: CONNECTED
⏳ Data channel state changed to: OPEN
⏳ Data channel OPEN - sending initial page data
⏳ Sent initial page data for page [...]
```

**If stuck at "Remote description set":**
- Connection is trying to establish
- Check for "Peer connection state changed" messages
- Check for ICE candidate messages

**If you see "Peer connection state changed to: FAILED":**
- Network/firewall issue
- ICE candidates not exchanging properly
- Try on different network

### 2. Browser Console (F12 → Console)

Look for these messages:

```
✅ Successfully decoded offer from URL
✅ Connection state: connected
⏳ Data channel opened
⏳ Received message: page_data
```

**If you see connection errors:**
- Check browser supports WebRTC
- Check no firewall blocking
- Look for ICE failure messages

### 3. Common Issues

#### Issue: "Peer connection state" never changes from answer processing

**Possible causes:**
- ICE candidates not being exchanged
- Firewall blocking UDP
- STUN server not reachable

**Test STUN server:**
```bash
# Test if STUN server is accessible
nc -u -v stun.l.google.com 19302
```

#### Issue: Peer connects but data channel never opens

**Possible causes:**
- Data channel negotiation failed
- Browser not receiving data channel
- Timing issue

**Check browser:**
Open browser console and look for:
- `RTCDataChannel` events
- `ondatachannel` callbacks
- Connection state logs

#### Issue: Data channel opens but no data received

**Check desktop logs for:**
```
Data channel OPEN - sending initial page data
Sent initial page data for page [...]
```

**If not seeing these:**
- sendInitialPageData() may be failing
- Check for exceptions
- Check page can be loaded

### 4. Manual Testing

#### Test ICE Connectivity

In browser console:
```javascript
// Check ICE connection state
console.log('ICE state:', peerConnection.iceConnectionState);
console.log('Connection state:', peerConnection.connectionState);
console.log('Signaling state:', peerConnection.signalingState);
```

#### Test Data Channel

In browser console:
```javascript
// Check data channel state
if (dataChannel) {
    console.log('Data channel state:', dataChannel.readyState);
    console.log('Data channel label:', dataChannel.label);
}
```

### 5. Expected Timeline

```
T+0s   User pastes answer in desktop app
T+0s   Desktop: Remote description set successfully
T+1s   Desktop: Peer connection state changed to: CONNECTING
T+2s   Desktop: ICE candidate generated (multiple)
T+3s   Browser: ICE candidates being gathered
T+5s   Desktop: Peer connection state changed to: CONNECTED
T+6s   Desktop: Data channel state changed to: OPEN
T+7s   Desktop: Sending initial page data
T+8s   Browser: Received message: page_data
T+9s   Browser: Page renders
```

**Total expected time:** 5-10 seconds

**If taking longer:**
- Check network latency
- Check if behind NAT/firewall
- May need TURN server

### 6. Quick Diagnostic Commands

**Desktop app logs:**
```bash
# Watch logs in real-time
tail -f ~/.drawboard/logs/app.log | grep -i "webrtc\|connection\|channel"
```

**Check Java process:**
```bash
# Verify app is running
ps aux | grep DrawboardApplication
```

**Browser console:**
```javascript
// Log all WebRTC events
peerConnection.addEventListener('connectionstatechange', () => {
    console.log('Connection state:', peerConnection.connectionState);
});

peerConnection.addEventListener('iceconnectionstatechange', () => {
    console.log('ICE connection state:', peerConnection.iceConnectionState);
});

if (dataChannel) {
    dataChannel.addEventListener('open', () => {
        console.log('Data channel opened!');
    });

    dataChannel.addEventListener('message', (event) => {
        console.log('Message received:', event.data.substring(0, 100));
    });
}
```

### 7. Network Requirements

**Firewall must allow:**
- ✅ UDP outbound (for STUN)
- ✅ UDP inbound (for ICE candidates)
- ✅ Access to stun.l.google.com:19302

**Test network:**
```bash
# Test UDP connectivity
echo "test" | nc -u -w1 stun.l.google.com 19302

# Check if behind symmetric NAT
# Use online tool: https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/
```

### 8. If Still Not Working

1. **Restart both sides:**
   - Close browser tab
   - Stop sharing in desktop app
   - Start fresh

2. **Try different network:**
   - Mobile hotspot
   - Different WiFi
   - Disable VPN

3. **Try different browser:**
   - Chrome (recommended)
   - Edge
   - Firefox

4. **Check logs for:**
   - Any ERROR messages
   - Any WARN messages
   - Stack traces

5. **Collect diagnostics:**
   - Complete desktop log
   - Browser console output
   - Network being used
   - OS and versions

---

## Next Step

Run the desktop app with these enhanced logs and look for the connection state progression. The logs will tell you exactly where it's getting stuck.

**If you see:**
- "Peer connection state changed to: CONNECTED" → Good! Waiting for data channel
- "Data channel state changed to: OPEN" → Great! Should send data now
- "Sent initial page data" → Perfect! Check browser

**If you DON'T see these:**
- Still at "Remote description set" → Connection establishing (wait 10s)
- No connection state change → Network/firewall issue
- Connection FAILED → NAT/firewall blocking

**Check browser console for matching events!**
