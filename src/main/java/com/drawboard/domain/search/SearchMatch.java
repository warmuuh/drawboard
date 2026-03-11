package com.drawboard.domain.search;

import io.avaje.jsonb.Json;

/**
 * Represents a single match within a search result.
 * Contains the matched content snippet with context and position information.
 */
@Json
public record SearchMatch(
    String elementId,
    SearchMatchType matchType,
    String snippet,
    int startOffset,
    int endOffset
) {
    /**
     * Creates a match for page or chapter name (which don't have element IDs).
     */
    public static SearchMatch forName(SearchMatchType matchType, String snippet,
                                     int startOffset, int endOffset) {
        return new SearchMatch(null, matchType, snippet, startOffset, endOffset);
    }
}
