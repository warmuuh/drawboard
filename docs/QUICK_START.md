# Quick Start Guide: WebRTC Page Sharing

## For Senders (Desktop App Users)

### Prerequisites
- Drawboard desktop application installed
- Active internet connection
- A page open in Drawboard

### Steps to Share

1. **Open a Page**
   - Open any notebook and select a page you want to share

2. **Click Share Button**
   - Look for the "Share" button in the toolbar (between Search and Tools)
   - Button is only enabled when a page is loaded

3. **Copy Share Link**
   - A dialog will appear with a long URL
   - Click "Copy" to copy the URL to your clipboard

4. **Send Link to Viewer**
   - Send the URL via:
     - Email
     - Slack/Teams
     - Text message
     - Any messaging app

5. **Wait for Answer**
   - The viewer will open your link and generate an "answer"
   - They will send this answer back to you

6. **Paste Answer**
   - Copy the answer they send you
   - Paste it into the "Answer" text area in the share dialog
   - Click "Connect"

7. **Start Sharing**
   - Connection will establish (may take a few seconds)
   - Status will show "Connected - sharing page in real-time"
   - Any edits you make will appear in the viewer after ~500ms

8. **Stop Sharing**
   - Click "Stop Sharing" button when done
   - Or simply close the dialog

### Tips for Senders
- ✅ Test with a friend first
- ✅ Keep the share dialog open while sharing
- ✅ Make sure your internet is stable
- ✅ Don't close the app while sharing
- ⚠️ Each share session supports one viewer
- ⚠️ Connection may fail behind strict firewalls

---

## For Viewers (Web Browser Users)

### Prerequisites
- Modern web browser (Chrome, Firefox, Safari, Edge)
- Active internet connection
- No account or installation required!

### Steps to View

1. **Receive Share Link**
   - Someone will send you a link that starts with:
   - `https://petermucha.github.io/drawboard/?offer=...`

2. **Open Link in Browser**
   - Click the link or paste it into your browser
   - Works on desktop and mobile devices

3. **Wait for Answer**
   - The page will automatically process the connection
   - An "answer" will be generated automatically
   - This takes just a second

4. **Copy Answer**
   - Click "Copy Answer" button
   - The answer is now in your clipboard

5. **Send Answer Back**
   - Send the answer to the person who shared the page
   - Use the same method they used to send you the link

6. **Wait for Connection**
   - Once they paste your answer, the connection will establish
   - This usually takes 5-10 seconds

7. **View Page**
   - The page will load automatically
   - You'll see everything they have on the page
   - Any edits they make appear in real-time

8. **Use Controls**
   - **Zoom In:** Click `+` button
   - **Zoom Out:** Click `−` button
   - **Reset:** Click `⟲` button
   - **Pan:** Click and drag on the canvas

9. **Disconnect**
   - Simply close the browser tab when done

### Tips for Viewers
- ✅ Use Chrome or Edge for best experience
- ✅ Enable pop-ups if needed
- ✅ Make sure camera/mic permissions aren't blocking WebRTC
- ✅ View in fullscreen for better experience
- ⚠️ Read-only - you cannot edit the page
- ⚠️ Connection may not work on restricted networks

---

## Troubleshooting

### Desktop App

**Problem:** "Share" button is disabled/grayed out
- **Solution:** Open a page first - the button only works when a page is loaded

**Problem:** Share link is not generated
- **Solution:** Check logs, ensure WebRTC library is loaded correctly

**Problem:** Connection fails after pasting answer
- **Solution:**
  - Verify the answer is complete (not cut off)
  - Check your internet connection
  - Try generating a new share link

**Problem:** Viewer reports not receiving updates
- **Solution:**
  - Make sure you're still connected (check status in dialog)
  - Try making a change and wait 1-2 seconds
  - Connection may have been lost - restart sharing

### Web Viewer

**Problem:** "No offer found in URL"
- **Solution:** Make sure you copied the complete URL including the `?offer=...` part

**Problem:** "Failed to decode offer from URL"
- **Solution:** The URL may have been corrupted during copy/paste. Ask for a new link.

**Problem:** Connection takes forever
- **Solution:**
  - Check your internet connection
  - Verify the desktop app user pasted your answer
  - Try refreshing and starting over

**Problem:** Page loads but images are missing
- **Solution:**
  - Wait a few seconds - images load after page structure
  - Check browser console for errors
  - May be a network issue

**Problem:** Updates not appearing
- **Solution:**
  - Check connection status (should say "Connected")
  - Ask sender if they're still editing
  - Try refreshing the page and reconnecting

### Network Issues

**Problem:** Connection fails immediately
- **Possible causes:**
  - Corporate firewall blocking WebRTC
  - VPN interfering with connection
  - Network restrictions
- **Solutions:**
  - Try on different network (mobile hotspot)
  - Disable VPN temporarily
  - Check with IT department about WebRTC

**Problem:** Connection works then drops
- **Solution:**
  - Unstable internet - try to stabilize connection
  - Sender may have closed the app
  - Reconnect by starting over

---

## FAQ

### General

**Q: Do I need an account?**
A: No! Completely account-free for both senders and viewers.

**Q: Is my data secure?**
A: Yes! Connections use end-to-end encryption (DTLS). No data passes through any server.

**Q: How many people can view at once?**
A: Currently, one viewer per share session. Multiple shares require separate sessions.

**Q: Does it work on mobile?**
A: Yes! The web viewer works on mobile browsers.

**Q: Can the viewer edit the page?**
A: No, viewers have read-only access.

**Q: What happens if I close the app/browser?**
A: The connection ends immediately. No reconnection logic (yet).

### Performance

**Q: How fast are updates?**
A: Updates appear within ~500ms of changes being made.

**Q: Does it use a lot of bandwidth?**
A: Initial load depends on page size. Updates are small (just the changes).

**Q: Can I share large pages?**
A: Yes, but initial load time increases with page size and number of images.

### Privacy

**Q: Where is my data stored?**
A: Nowhere! It's a direct peer-to-peer connection.

**Q: Can others intercept my data?**
A: No, WebRTC uses encryption (DTLS) by default.

**Q: How long does a session last?**
A: Until you close the app/browser or click "Stop Sharing".

---

## Advanced Usage

### Using a Different Viewer URL

If you want to host your own viewer:

1. Copy the `docs/` folder to your web server
2. Update `DEFAULT_VIEWER_URL` in `WebRTCShareService.java`:
   ```java
   private static final String DEFAULT_VIEWER_URL = "https://your-domain.com/";
   ```
3. Recompile the desktop app

### Debugging Connection Issues

**Desktop App:**
Check logs for WebRTC errors:
```
grep -i "webrtc\|share" ~/.drawboard/logs/app.log
```

**Web Viewer:**
Open browser DevTools (F12) and check Console for errors.

---

## Need Help?

- **Documentation:** See `WEBRTC_IMPLEMENTATION.md` for technical details
- **Issues:** Report bugs at the project repository
- **Questions:** Contact the development team

---

## Next Steps

Once you're comfortable with basic sharing:
- Experiment with different page types (text, images, drawings)
- Test on different networks
- Try mobile viewing
- Explore the zoom and pan controls

Enjoy real-time collaboration with Drawboard! 🎨
