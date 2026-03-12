# Quick Workaround: Reverse Connection Flow

## Problem
webrtc-java 0.14.0 cannot parse modern browser SDP answers due to `a=mid:0` attribute.

## Solution
**Reverse the connection flow** - have browser create offer, desktop create answer.

## Why This Might Work

- Desktop app (with older library) will process browser's offer
- Desktop app will generate simpler answer
- Browser should handle any answer format (more tolerant)

## Implementation Steps

This is a **quick test** to see if it solves the compatibility issue:

### 1. Create New JavaScript Client (Offer Mode)

Create `docs/js/webrtc-client-offer.js`:

```javascript
class WebRTCClientOfferMode {
    constructor() {
        this.peerConnection = null;
        this.dataChannel = null;
    }

    async createOffer() {
        const config = {
            iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
        };

        this.peerConnection = new RTCPeerConnection(config);

        // Create data channel (browser initiates)
        this.dataChannel = this.peerConnection.createDataChannel('page-data');
        this.setupDataChannel();

        // Create offer
        const offer = await this.peerConnection.createOffer();
        await this.peerConnection.setLocalDescription(offer);

        // Return offer SDP
        return offer.sdp;
    }

    async processAnswer(answerSdp) {
        const answer = {
            type: 'answer',
            sdp: answerSdp
        };

        await this.peerConnection.setRemoteDescription(answer);
    }

    setupDataChannel() {
        this.dataChannel.onopen = () => {
            console.log('Data channel opened - ready to receive');
            if (this.onDataChannelOpen) {
                this.onDataChannelOpen();
            }
        };

        this.dataChannel.onmessage = (event) => {
            if (this.onMessage) {
                this.onMessage(event.data);
            }
        };
    }
}
```

### 2. Update Java Service (Answer Mode)

Add method to `WebRTCShareService.java`:

```java
public String processOfferAndCreateAnswer(String pageId, String offerSdp) {
    PageShareSession session = activeSessions.get(pageId);
    if (session == null) {
        throw new IllegalArgumentException("No active session for page: " + pageId);
    }

    try {
        // Set remote description (offer from browser)
        RTCSessionDescription offer = new RTCSessionDescription(RTCSdpType.OFFER, offerSdp);
        CountDownLatch setRemoteLatch = new CountDownLatch(1);

        session.peerConnection().setRemoteDescription(offer, new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {
                log.info("Set remote offer successfully");
                setRemoteLatch.countDown();
            }

            @Override
            public void onFailure(String error) {
                log.error("Failed to set remote offer: {}", error);
                setRemoteLatch.countDown();
            }
        });

        if (!setRemoteLatch.await(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("Timeout setting remote offer");
        }

        // Create answer
        RTCAnswerOptions answerOptions = new RTCAnswerOptions();
        CountDownLatch answerLatch = new CountDownLatch(1);
        String[] answerHolder = new String[1];

        session.peerConnection().createAnswer(answerOptions, new CreateSessionDescriptionObserver() {
            @Override
            public void onSuccess(RTCSessionDescription description) {
                session.peerConnection().setLocalDescription(description, new SetSessionDescriptionObserver() {
                    @Override
                    public void onSuccess() {
                        answerHolder[0] = description.sdp;
                        answerLatch.countDown();
                    }

                    @Override
                    public void onFailure(String error) {
                        log.error("Failed to set local answer: {}", error);
                        answerLatch.countDown();
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                log.error("Failed to create answer: {}", error);
                answerLatch.countDown();
            }
        });

        if (!answerLatch.await(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("Timeout creating answer");
        }

        log.info("Generated answer SDP");
        return answerHolder[0];

    } catch (Exception e) {
        log.error("Failed to process offer and create answer", e);
        throw new RuntimeException("Failed to process offer", e);
    }
}
```

### 3. Update Flow

**New Flow:**
1. User clicks "Share" → Desktop creates session (but NO offer yet)
2. User gets a session ID/URL → Opens in browser
3. Browser creates offer → Shows to user
4. User pastes offer in desktop app
5. Desktop processes offer → Generates answer
6. Desktop shows answer → User copies
7. User pastes answer in browser
8. Connection established!

## Why This Approach

**Theory:**
- webrtc-java might handle browser's OFFER better than browser's ANSWER
- Desktop-generated ANSWER might be simpler/older format
- Browser is more tolerant of older SDP formats

**Risk:**
- Browser's offer might still have attributes desktop can't parse
- More confusing for users (extra step)
- May not actually solve the issue

## Quick Test

Without full implementation, you can test if desktop can parse browser offer:

1. Open browser console on any WebRTC test page
2. Create offer:
```javascript
const pc = new RTCPeerConnection();
const dc = pc.createDataChannel('test');
const offer = await pc.createOffer();
console.log(offer.sdp);
```

3. Copy that SDP
4. In desktop app, try to set it as remote description
5. See if it accepts browser's offer format

## Alternative: Check webrtc-java Updates

```bash
# Check Maven Central for newer versions
curl -s "https://search.maven.org/solrsearch/select?q=g:dev.onvoid.webrtc+AND+a:webrtc-java&rows=20&wt=json" | jq '.response.docs[] | {version: .v, timestamp: .timestamp}'
```

If 0.15+ exists, upgrading might be simpler than reversing the flow.

## Recommendation

1. **First**: Check if webrtc-java 0.15+ exists
2. **Second**: Try reversed flow if no newer version
3. **Third**: Consider switching to libjitsi or other library

The fundamental issue is that webrtc-java 0.14.0 has incomplete modern WebRTC support. This is not something we can fully work around with SDP munging.
