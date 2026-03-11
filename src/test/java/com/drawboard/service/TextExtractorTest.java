package com.drawboard.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextExtractorTest {

    @Test
    void testExtractPlainTextFromSimpleHTML() {
        String html = "<p>Hello world</p>";
        String result = TextExtractor.extractPlainText(html);
        assertEquals("Hello world", result);
    }

    @Test
    void testExtractPlainTextWithMultipleTags() {
        String html = "<p>Hello <b>bold</b> and <i>italic</i> text</p>";
        String result = TextExtractor.extractPlainText(html);
        assertEquals("Hello bold and italic text", result);
    }

    @Test
    void testExtractPlainTextWithNestedTags() {
        String html = "<div><p>Paragraph <span>with <b>nested</b> tags</span></p></div>";
        String result = TextExtractor.extractPlainText(html);
        assertEquals("Paragraph with nested tags", result);
    }

    @Test
    void testExtractPlainTextWithHTMLEntities() {
        String html = "<p>&lt;tag&gt; &amp; &quot;quotes&quot;</p>";
        String result = TextExtractor.extractPlainText(html);
        assertEquals("<tag> & \"quotes\"", result);
    }

    @Test
    void testExtractPlainTextWithNbsp() {
        String html = "<p>Multiple&nbsp;&nbsp;spaces</p>";
        String result = TextExtractor.extractPlainText(html);
        assertEquals("Multiple spaces", result);
    }

    @Test
    void testExtractPlainTextNormalizeWhitespace() {
        String html = "<p>Too    many\n\n\nspaces</p>";
        String result = TextExtractor.extractPlainText(html);
        assertEquals("Too many spaces", result);
    }

    @Test
    void testExtractPlainTextEmpty() {
        String html = "";
        String result = TextExtractor.extractPlainText(html);
        assertEquals("", result);
    }

    @Test
    void testExtractPlainTextNull() {
        String result = TextExtractor.extractPlainText(null);
        assertEquals("", result);
    }

    @Test
    void testCreateSnippetSimple() {
        String text = "The quick brown fox jumps over the lazy dog";
        String snippet = TextExtractor.createSnippet(text, 16, 19, 10);

        // Should include "fox" with some context
        assertTrue(snippet.contains("fox"));
    }

    @Test
    void testCreateSnippetWithEllipsisAtStart() {
        String text = "The quick brown fox jumps over the lazy dog";
        String snippet = TextExtractor.createSnippet(text, 32, 36, 5);

        // Should have ellipsis at start since we're far from beginning
        assertTrue(snippet.startsWith("..."));
        assertTrue(snippet.contains("lazy"));
    }

    @Test
    void testCreateSnippetWithEllipsisAtEnd() {
        String text = "The quick brown fox jumps over the lazy dog";
        String snippet = TextExtractor.createSnippet(text, 4, 9, 5);

        // Should have ellipsis at end since there's more text after
        assertTrue(snippet.endsWith("..."));
        assertTrue(snippet.contains("quick"));
    }

    @Test
    void testCreateSnippetFullText() {
        String text = "Short text";
        String snippet = TextExtractor.createSnippet(text, 0, 5, 100);

        // Should not have ellipsis when context covers entire text
        assertEquals("Short text", snippet);
    }

    @Test
    void testCreateSnippetAtStart() {
        String text = "The quick brown fox jumps over the lazy dog";
        String snippet = TextExtractor.createSnippet(text, 0, 3, 10);

        // Should not have ellipsis at start
        assertFalse(snippet.startsWith("..."));
        assertTrue(snippet.startsWith("The"));
    }

    @Test
    void testCreateSnippetAtEnd() {
        String text = "The quick brown fox jumps over the lazy dog";
        String snippet = TextExtractor.createSnippet(text, 40, 43, 10);

        // Should not have ellipsis at end
        assertFalse(snippet.endsWith("..."));
        assertTrue(snippet.endsWith("dog"));
    }

    @Test
    void testCreateSnippetEmpty() {
        String snippet = TextExtractor.createSnippet("", 0, 0, 10);
        assertEquals("", snippet);
    }

    @Test
    void testCreateSnippetNull() {
        String snippet = TextExtractor.createSnippet(null, 0, 0, 10);
        assertEquals("", snippet);
    }

    @Test
    void testCreateSnippetWordBoundaries() {
        String text = "The quick brown fox jumps";
        String snippet = TextExtractor.createSnippet(text, 10, 15, 3);

        // Should break at word boundaries, not in middle of words
        // "brown" is at position 10-15, with 3 chars context expands to include "quick" and "fox"
        assertTrue(snippet.contains("brown"));
        // Should not cut words in half - should have complete words
        assertTrue(snippet.contains("quick") || snippet.contains("fox"));
    }
}
