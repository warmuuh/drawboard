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
    private String backgroundColor = "#FFFACD"; // Default light yellow
    private java.util.function.Consumer<WebView> onWebViewClicked;

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

        // Set WebView background to match canvas background
        webView.setStyle("-fx-background-color: " + backgroundColor + ";");

        // Intercept parent's mouse events and re-dispatch to WebView
        webView.setPickOnBounds(false); // Only respond to actual content, not transparent areas

        // Allow middle-button events to pass through for canvas panning
        // Use event filter to catch the event before WebView consumes it
        webView.addEventFilter(javafx.scene.input.MouseEvent.ANY, event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.MIDDLE ||
                event.isMiddleButtonDown()) {
                // Let the event pass through to parent for panning
                event.consume(); // Consume it here to prevent WebView from handling it

                // Re-fire the event on the parent so the tool can handle it
                javafx.event.Event.fireEvent(webView.getParent(), event.copyFor(event.getSource(), webView.getParent()));
            }
        });

        // Add click handler to notify when WebView is clicked (for selection support)
        // Use event filter to catch the event before WebView processes it
        webView.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
            if (onWebViewClicked != null && event.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                // Check if the click is actually on the visible WebView
                // by converting to scene coordinates and checking bounds
                javafx.geometry.Bounds boundsInScene = webView.localToScene(webView.getBoundsInLocal());
                if (boundsInScene.contains(event.getSceneX(), event.getSceneY())) {
                    onWebViewClicked.accept(webView);
                }
            }
        });

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

    public void setOnWebViewClicked(java.util.function.Consumer<WebView> listener) {
        this.onWebViewClicked = listener;
    }

    public void setBackgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public String wrapHtmlContent(String htmlContent) {
        // Wrap content in a complete HTML document with proper styling
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    html, body {
                        margin: 0;
                        padding: 8px;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        font-size: 14px;
                        color: #333;
                        background: %s;
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
            """.formatted(backgroundColor, htmlContent);
    }
}
