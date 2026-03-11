package com.drawboard.domain.search;

/**
 * Type of content that was matched in a search.
 */
public enum SearchMatchType {
    /**
     * Match found in page name
     */
    PAGE_NAME,

    /**
     * Match found in chapter name
     */
    CHAPTER_NAME,

    /**
     * Match found in text element content
     */
    TEXT_ELEMENT,

    /**
     * Match found in drawing element annotation (future)
     */
    DRAWING_ANNOTATION,

    /**
     * Match found in image OCR text (future)
     */
    IMAGE_TEXT
}
