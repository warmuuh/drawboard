package com.drawboard.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownFormatterTest {

    @Test
    void testBasicFormatting() {
        String markdown = "**bold** and *italic* text";
        String html = MarkdownFormatter.toHtml(markdown);
        assertTrue(html.contains("<strong>bold</strong>"));
        assertTrue(html.contains("<em>italic</em>"));
    }

    @Test
    void testHeaders() {
        String markdown = "# Header 1\n## Header 2\n### Header 3";
        String html = MarkdownFormatter.toHtml(markdown);
        assertTrue(html.contains("<h1>Header 1</h1>"));
        assertTrue(html.contains("<h2>Header 2</h2>"));
        assertTrue(html.contains("<h3>Header 3</h3>"));
    }

    @Test
    void testCodeBlocks() {
        String markdown = "```java\nString code = \"test\";\n```";
        String html = MarkdownFormatter.toHtml(markdown);
        assertTrue(html.contains("<pre><code"));
        assertTrue(html.contains("String code"));
    }

    @Test
    void testInlineCode() {
        String markdown = "Use `inline code` here";
        String html = MarkdownFormatter.toHtml(markdown);
        assertTrue(html.contains("<code>inline code</code>"));
    }

    @Test
    void testLinks() {
        String markdown = "[link text](https://example.com)";
        String html = MarkdownFormatter.toHtml(markdown);
        assertTrue(html.contains("<a href=\"https://example.com\">link text</a>"));
    }

    @Test
    void testLists() {
        String markdown = "- Item 1\n- Item 2\n- Item 3";
        String html = MarkdownFormatter.toHtml(markdown);
        assertTrue(html.contains("<ul>"));
        assertTrue(html.contains("<li>Item 1</li>"));
        assertTrue(html.contains("<li>Item 2</li>"));
    }

    @Test
    void testOrderedLists() {
        String markdown = "1. First\n2. Second\n3. Third";
        String html = MarkdownFormatter.toHtml(markdown);
        assertTrue(html.contains("<ol>"));
        assertTrue(html.contains("<li>First</li>"));
        assertTrue(html.contains("<li>Second</li>"));
    }

    @Test
    void testWikilinks() {
        String markdown = "See [[page name]] for details";
        String html = MarkdownFormatter.toHtml(markdown);
        assertTrue(html.contains("page name"));
        assertFalse(html.contains("[["));
    }

    @Test
    void testWikilinksWithDisplay() {
        String markdown = "See [[page|display text]] for details";
        String html = MarkdownFormatter.toHtml(markdown);
        assertTrue(html.contains("display text"));
        assertFalse(html.contains("[["));
        assertFalse(html.contains("page"));
    }

    @Test
    void testStrikethrough() {
        String markdown = "~~strikethrough~~";
        String html = MarkdownFormatter.toHtml(markdown);
        assertTrue(html.contains("<del>strikethrough</del>"));
    }

    @Test
    void testTables() {
        String markdown = "| Column 1 | Column 2 |\n|----------|----------|\n| Cell 1   | Cell 2   |";
        String html = MarkdownFormatter.toHtml(markdown);
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("<th>Column 1</th>"));
        assertTrue(html.contains("<td>Cell 1</td>"));
    }

    @Test
    void testExtractImages() {
        String markdown = "Here's an image: ![alt text](path/to/image.png) and another ![[image2.jpg]]";
        List<MarkdownFormatter.ImageReference> images = MarkdownFormatter.extractImages(markdown);

        assertEquals(2, images.size());
        assertEquals("path/to/image.png", images.get(0).path());
        assertEquals("alt text", images.get(0).alt());
        assertEquals("image2.jpg", images.get(1).path());
    }

    @Test
    void testRemoveImages() {
        String markdown = "Text before ![alt](image.png) text after";
        String result = MarkdownFormatter.removeImages(markdown);
        assertTrue(result.contains("Text before"));
        assertTrue(result.contains("alt"));
        assertTrue(result.contains("text after"));
        assertFalse(result.contains("!["));
    }

    @Test
    void testEmptyString() {
        assertEquals("", MarkdownFormatter.toHtml(null));
        assertEquals("", MarkdownFormatter.toHtml(""));
        assertEquals("", MarkdownFormatter.toHtml("   "));
    }

    @Test
    void testComplexMarkdown() {
        String markdown = """
            # Main Title

            This is a paragraph with **bold** and *italic* text.

            ## Code Example

            ```java
            public class Test {
                private String name;
            }
            ```

            ## List of Items

            - First item with `inline code`
            - Second item with [a link](https://example.com)
            - Third item with ~~strikethrough~~

            Check [[WikiPage]] for more info.
            """;

        String html = MarkdownFormatter.toHtml(markdown);

        assertTrue(html.contains("<h1>Main Title</h1>"));
        assertTrue(html.contains("<h2>Code Example</h2>"));
        assertTrue(html.contains("<strong>bold</strong>"));
        assertTrue(html.contains("<em>italic</em>"));
        assertTrue(html.contains("<pre><code"));
        assertTrue(html.contains("<ul>"));
        assertTrue(html.contains("<code>inline code</code>"));
        assertTrue(html.contains("<a href=\"https://example.com\">a link</a>"));
        assertTrue(html.contains("<del>strikethrough</del>"));
        assertTrue(html.contains("WikiPage"));
    }
}
