package com.drawboard.webrtc;

import com.drawboard.domain.Page;

/**
 * Event fired when a page is updated.
 * Used to notify WebRTC share sessions of changes.
 */
public record PageUpdateEvent(
    String notebookId,
    String chapterId,
    String pageId,
    Page page
) {}
