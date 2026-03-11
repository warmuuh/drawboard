package com.drawboard.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to convert Markdown syntax to HTML rich text.
 * Uses regex-based parsing for MVP implementation.
 */
public class MarkdownFormatter {

    /**
     * Convert Markdown text to HTML rich text.
     */
    public static String toHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        String html = markdown;

        // Convert headers (must be done before other inline formatting)
        html = convertHeaders(html);

        // Convert code blocks (before inline code)
        html = convertCodeBlocks(html);

        // Convert inline code
        html = convertInlineCode(html);

        // Convert bold (before italic to handle ***)
        html = convertBold(html);

        // Convert italic
        html = convertItalic(html);

        // Convert links
        html = convertLinks(html);

        // Convert wikilinks
        html = convertWikilinks(html);

        // Convert lists
        html = convertLists(html);

        // Convert line breaks
        html = html.replace("\n", "<br>");

        return html;
    }

    private static String convertHeaders(String text) {
        Pattern pattern = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            int level = matcher.group(1).length();
            String content = matcher.group(2);
            matcher.appendReplacement(result, "<h" + level + ">" + content + "</h" + level + ">");
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static String convertCodeBlocks(String text) {
        // Match ```lang\ncode\n``` or ```\ncode\n```
        Pattern pattern = Pattern.compile("```(?:\\w+)?\\n([\\s\\S]*?)```", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String code = matcher.group(1);
            // Escape HTML in code
            code = escapeHtml(code);
            matcher.appendReplacement(result, "<pre><code>" + code + "</code></pre>");
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static String convertInlineCode(String text) {
        Pattern pattern = Pattern.compile("`([^`]+)`");
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String code = escapeHtml(matcher.group(1));
            matcher.appendReplacement(result, "<code>" + code + "</code>");
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static String convertBold(String text) {
        // Match **text** or __text__
        Pattern pattern = Pattern.compile("(\\*\\*|__)(.+?)\\1");
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String content = matcher.group(2);
            matcher.appendReplacement(result, "<strong>" + content + "</strong>");
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static String convertItalic(String text) {
        // Match *text* or _text_ (but not ** or __)
        Pattern pattern = Pattern.compile("(?<!\\*)\\*(?!\\*)([^*]+)\\*(?!\\*)|(?<!_)_(?!_)([^_]+)_(?!_)");
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String content = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            matcher.appendReplacement(result, "<em>" + content + "</em>");
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static String convertLinks(String text) {
        // Match [text](url)
        Pattern pattern = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String linkText = matcher.group(1);
            String url = matcher.group(2);
            matcher.appendReplacement(result, "<a href=\"" + url + "\">" + linkText + "</a>");
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static String convertWikilinks(String text) {
        // Match [[page]] or [[page|display text]]
        Pattern pattern = Pattern.compile("\\[\\[([^|\\]]+)(?:\\|([^\\]]+))?\\]\\]");
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String page = matcher.group(1);
            String displayText = matcher.group(2) != null ? matcher.group(2) : page;
            // For MVP, just convert to plain text (could be enhanced to link internally)
            matcher.appendReplacement(result, displayText);
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static String convertLists(String text) {
        String[] lines = text.split("\n");
        StringBuilder result = new StringBuilder();
        boolean inUnorderedList = false;
        boolean inOrderedList = false;

        for (String line : lines) {
            String trimmed = line.trim();

            // Unordered list item
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                if (!inUnorderedList) {
                    result.append("<ul>");
                    inUnorderedList = true;
                }
                if (inOrderedList) {
                    result.append("</ol>");
                    inOrderedList = false;
                }
                String content = trimmed.substring(2);
                result.append("<li>").append(content).append("</li>");
            }
            // Ordered list item
            else if (trimmed.matches("^\\d+\\.\\s+.+")) {
                if (!inOrderedList) {
                    result.append("<ol>");
                    inOrderedList = true;
                }
                if (inUnorderedList) {
                    result.append("</ul>");
                    inUnorderedList = false;
                }
                String content = trimmed.replaceFirst("^\\d+\\.\\s+", "");
                result.append("<li>").append(content).append("</li>");
            }
            // Not a list item
            else {
                if (inUnorderedList) {
                    result.append("</ul>");
                    inUnorderedList = false;
                }
                if (inOrderedList) {
                    result.append("</ol>");
                    inOrderedList = false;
                }
                result.append(line).append("\n");
            }
        }

        // Close any open lists
        if (inUnorderedList) {
            result.append("</ul>");
        }
        if (inOrderedList) {
            result.append("</ol>");
        }

        return result.toString();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * Extract image references from markdown text.
     */
    public static List<ImageReference> extractImages(String markdown) {
        List<ImageReference> images = new ArrayList<>();
        if (markdown == null) {
            return images;
        }

        // Match ![alt](path) or ![[path]]
        Pattern pattern = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)|!\\[\\[([^\\]]+)\\]\\]");
        Matcher matcher = pattern.matcher(markdown);

        while (matcher.find()) {
            String path;
            String alt;
            if (matcher.group(2) != null) {
                // ![alt](path) format
                alt = matcher.group(1);
                path = matcher.group(2);
            } else {
                // ![[path]] format
                path = matcher.group(3);
                alt = "";
            }
            images.add(new ImageReference(path, alt, matcher.start(), matcher.end()));
        }

        return images;
    }

    /**
     * Remove image syntax from markdown, leaving just the alt text or empty string.
     */
    public static String removeImages(String markdown) {
        if (markdown == null) {
            return "";
        }

        // Replace ![alt](path) with alt text
        String result = markdown.replaceAll("!\\[([^\\]]*)\\]\\([^)]+\\)", "$1");
        // Replace ![[path]] with empty string
        result = result.replaceAll("!\\[\\[[^\\]]+\\]\\]", "");

        return result;
    }

    /**
     * Represents an image reference found in markdown.
     */
    public record ImageReference(String path, String alt, int startPos, int endPos) {}
}
