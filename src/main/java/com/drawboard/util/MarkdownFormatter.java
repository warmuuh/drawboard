package com.drawboard.util;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to convert Markdown syntax to HTML rich text.
 * Uses CommonMark library for robust parsing.
 */
public class MarkdownFormatter {

    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;

    static {
        // Configure parser with GFM extensions (tables, strikethrough)
        List<Extension> extensions = List.of(
            TablesExtension.create(),
            StrikethroughExtension.create()
        );

        PARSER = Parser.builder()
            .extensions(extensions)
            .build();

        RENDERER = HtmlRenderer.builder()
            .extensions(extensions)
            .build();
    }

    /**
     * Convert Markdown text to HTML rich text.
     */
    public static String toHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        // First handle Obsidian-specific wikilinks (not part of standard Markdown)
        String preprocessed = convertWikilinks(markdown);

        // Parse and render using CommonMark
        Node document = PARSER.parse(preprocessed);
        String html = RENDERER.render(document);

        // CommonMark wraps everything in <p> tags by default, which may not be desired
        // for single-line content. Strip outer paragraph tags for cleaner rendering.
        html = html.trim();
        if (html.startsWith("<p>") && html.endsWith("</p>") && html.indexOf("<p>", 1) == -1) {
            html = html.substring(3, html.length() - 4);
        }

        return html;
    }

    /**
     * Convert Obsidian wikilinks to standard Markdown links or plain text.
     * Handles [[page]] and [[page|display text]] syntax.
     */
    private static String convertWikilinks(String text) {
        // Match [[page]] or [[page|display text]]
        Pattern pattern = Pattern.compile("\\[\\[([^|\\]]+)(?:\\|([^\\]]+))?\\]\\]");
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String page = matcher.group(1);
            String displayText = matcher.group(2) != null ? matcher.group(2) : page;
            // For MVP, just convert to plain text (could be enhanced to link internally)
            // Escape any special regex characters in the replacement
            matcher.appendReplacement(result, Matcher.quoteReplacement(displayText));
        }
        matcher.appendTail(result);

        return result.toString();
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
