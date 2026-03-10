package com.drawboard.domain.preferences;

import io.avaje.jsonb.Json;

import java.time.Instant;

/**
 * Tracks the last opened page for auto-restore on startup.
 */
@Json
public record LastOpenedPage(
    String notebookId,
    String chapterId,
    String pageId,
    Instant lastOpened
) {}
