package com.drawboard.service;

import java.util.regex.Pattern;

/**
 * Utility for extracting plain text from formatted content.
 * Used by search to extract searchable text from HTML and other formats.
 */
public class TextExtractor {
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern HTML_ENTITY_PATTERN = Pattern.compile("&[a-zA-Z]+;|&#\\d+;");

    /**
     * Extracts plain text from HTML content by removing tags and decoding entities.
     * This is a simple implementation - for production, consider using a proper HTML parser.
     */
    public static String extractPlainText(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return "";
        }

        // Remove HTML tags
        String text = HTML_TAG_PATTERN.matcher(htmlContent).replaceAll("");

        // Decode common HTML entities
        text = text.replace("&nbsp;", " ")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&amp;", "&")
                   .replace("&quot;", "\"")
                   .replace("&apos;", "'");

        // Decode numeric entities (basic support)
        text = HTML_ENTITY_PATTERN.matcher(text).replaceAll(" ");

        // Normalize whitespace
        text = text.replaceAll("\\s+", " ").trim();

        return text;
    }

    /**
     * Creates a snippet of text around a match position.
     * @param text Full text content
     * @param matchStart Start position of match
     * @param matchEnd End position of match
     * @param contextLength Number of characters to include before and after match
     * @return Snippet with context, potentially with ellipsis
     */
    public static String createSnippet(String text, int matchStart, int matchEnd, int contextLength) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Calculate snippet boundaries
        int snippetStart = Math.max(0, matchStart - contextLength);
        int snippetEnd = Math.min(text.length(), matchEnd + contextLength);

        // Find word boundaries to avoid cutting words
        snippetStart = findWordBoundaryBackward(text, snippetStart);
        snippetEnd = findWordBoundaryForward(text, snippetEnd);

        // Build snippet with ellipsis if needed
        StringBuilder snippet = new StringBuilder();
        if (snippetStart > 0) {
            snippet.append("...");
        }
        snippet.append(text, snippetStart, snippetEnd);
        if (snippetEnd < text.length()) {
            snippet.append("...");
        }

        return snippet.toString();
    }

    private static int findWordBoundaryBackward(String text, int position) {
        if (position == 0) {
            return 0;
        }
        while (position > 0 && !Character.isWhitespace(text.charAt(position - 1))) {
            position--;
        }
        return position;
    }

    private static int findWordBoundaryForward(String text, int position) {
        if (position >= text.length()) {
            return text.length();
        }
        while (position < text.length() && !Character.isWhitespace(text.charAt(position))) {
            position++;
        }
        return position;
    }
}
