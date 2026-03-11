package com.drawboard.domain.search;

import io.avaje.jsonb.Json;

/**
 * Represents a single search result containing location and match information.
 */
@Json
public record SearchResult(
    String notebookId,
    String notebookName,
    String chapterId,
    String chapterName,
    String pageId,
    String pageName,
    SearchMatch match
) {}
