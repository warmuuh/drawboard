package com.drawboard.canvas;

import com.drawboard.domain.elements.TextElement;
import javafx.scene.Node;
import javafx.scene.web.WebView;

import java.util.function.BiConsumer;

/**
 * Renders text elements using WebView with HTML content.
 * This allows rich text formatting with bold, italic, headings, etc.
 */
public class TextElementRenderer {

    private BiConsumer<String, String> onContentChanged;

    public Node render(TextElement element) {
        WebView webView = new WebView();

        // Set position and size
        webView.setLayoutX(element.x());
        webView.setLayoutY(element.y());
        webView.setPrefWidth(element.width());
        webView.setPrefHeight(element.height());

        // Load HTML content
        String html = wrapHtmlContent(element.htmlContent());
        webView.getEngine().loadContent(html);

        // Enable editing once the document is loaded
        webView.getEngine().documentProperty().addListener((obs, oldDoc, newDoc) -> {
            if (newDoc != null) {
                webView.getEngine().executeScript("document.body.contentEditable = 'true';");

                // Disable scrollbars
                webView.getEngine().executeScript("""
                    document.body.style.overflow = 'auto';
                    document.documentElement.style.overflow = 'auto';
                    """);

                // Listen for focus loss to save changes
                webView.focusedProperty().addListener((obsFocus, wasFocused, isNowFocused) -> {
                    if (wasFocused && !isNowFocused) {
                        // Lost focus - save the content
                        saveContent(webView, element.id());
                    }
                });
            }
        });

        // Enable context menu for text operations
        webView.setContextMenuEnabled(true);

        // Style to remove default margins
        webView.setStyle("-fx-background-color: transparent;");

        // Intercept parent's mouse events and re-dispatch to WebView
        webView.setPickOnBounds(false); // Only respond to actual content, not transparent areas

        return webView;
    }

    private void saveContent(WebView webView, String elementId) {
        try {
            String content = (String) webView.getEngine().executeScript(
                "document.body.innerHTML"
            );
            if (content != null && onContentChanged != null) {
                onContentChanged.accept(elementId, content);
            }
        } catch (Exception e) {
            // Ignore errors during content extraction
        }
    }

    public void setOnContentChanged(BiConsumer<String, String> listener) {
        this.onContentChanged = listener;
    }

    public String wrapHtmlContent(String htmlContent) {
        // Wrap content in a complete HTML document with proper styling
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body {
                        margin: 0;
                        padding: 8px;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        font-size: 14px;
                        color: #333;
                    }
                    p { margin: 0 0 8px 0; }
                    h1 { font-size: 24px; margin: 0 0 12px 0; }
                    h2 { font-size: 20px; margin: 0 0 10px 0; }
                    h3 { font-size: 16px; margin: 0 0 8px 0; }
                </style>
            </head>
            <body>
                %s
            </body>
            </html>
            """.formatted(htmlContent);
    }
}
