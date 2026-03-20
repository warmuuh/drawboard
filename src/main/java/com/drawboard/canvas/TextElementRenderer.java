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
    private java.util.function.BiConsumer<String, Double> onHeightChanged; // elementId, newHeight

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

                // Disable scrollbars completely
                webView.getEngine().executeScript("""
                    document.body.style.overflow = 'hidden';
                    document.documentElement.style.overflow = 'hidden';
                    """);

                // Set up auto-resize based on content height
                setupAutoResize(webView, element.id());

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

    private void setupAutoResize(WebView webView, String elementId) {
        // Make sure width is fixed (not auto-growing)
        webView.setMinWidth(webView.getPrefWidth());
        webView.setMaxWidth(webView.getPrefWidth());

        // Set up mutation observer to detect content changes
        webView.getEngine().executeScript("""
            var observer = new MutationObserver(function() {
                // Force a reflow to get accurate height
                document.body.offsetHeight;
                // Get the actual content height using offsetHeight which is more reliable
                var height = document.body.offsetHeight;
                // Store it so Java can read it
                window.contentHeight = height;
            });
            observer.observe(document.body, {
                childList: true,
                subtree: true,
                characterData: true
            });
            // Initial height
            window.contentHeight = document.body.offsetHeight;
            """);

        // Poll for height changes (less frequently to reduce flicker)
        javafx.animation.Timeline timeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(
                javafx.util.Duration.millis(200),
                event -> {
                    try {
                        Object heightObj = webView.getEngine().executeScript("window.contentHeight");
                        if (heightObj instanceof Number) {
                            double contentHeight = ((Number) heightObj).doubleValue();
                            double minHeight = 40; // Minimum height
                            // Add 16px padding (8px top + 8px bottom from body padding)
                            double newHeight = Math.max(minHeight, contentHeight);

                            // Only resize if height changed significantly (more than 3px to avoid micro-adjustments)
                            if (Math.abs(webView.getPrefHeight() - newHeight) > 3) {
                                webView.setPrefHeight(newHeight);
                                webView.setMinHeight(newHeight);
                                webView.setMaxHeight(newHeight);
                                if (onHeightChanged != null) {
                                    onHeightChanged.accept(elementId, newHeight);
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Ignore errors during height check
                    }
                }
            )
        );
        timeline.setCycleCount(javafx.animation.Timeline.INDEFINITE);
        timeline.play();

        // Stop timeline when WebView is removed from scene
        webView.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                timeline.stop();
            }
        });
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

    public void setOnHeightChanged(java.util.function.BiConsumer<String, Double> listener) {
        this.onHeightChanged = listener;
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
                    * {
                        box-sizing: border-box;
                    }
                    html, body {
                        margin: 0;
                        padding: 8px;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        font-size: 14px;
                        color: #333;
                        background: %s;
                        width: 100%%;
                        min-height: 24px;
                        word-wrap: break-word;
                        overflow-wrap: break-word;
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
