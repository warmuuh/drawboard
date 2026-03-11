package com.drawboard.canvas.tools;

import com.drawboard.domain.elements.TextElement;
import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Text tool for adding text elements to the canvas.
 * Click to create a new text box at the click location.
 */
public class TextTool extends AbstractTool {
    private static final Logger log = LoggerFactory.getLogger(TextTool.class);

    private Consumer<TextElement> onTextElementCreated;

    private static final double DEFAULT_WIDTH = 300;
    private static final double DEFAULT_HEIGHT = 100;

    public TextTool(Pane canvasContainer, Pane elementsPane) {
        super(canvasContainer, elementsPane);
    }

    @Override
    protected void handleMousePressed(MouseEvent event) {

        // Check if clicking on an existing text element
        javafx.scene.Node clickedNode = findTextNodeAt(event.getX(), event.getY());

        if (clickedNode instanceof javafx.scene.web.WebView webView) {
            // Clicking on existing WebView - focus it and fire a new mouse event
            webView.requestFocus();

            // Calculate position relative to WebView
            javafx.geometry.Point2D localPoint = webView.sceneToLocal(event.getSceneX(), event.getSceneY());

            // Fire a new mouse event directly to the WebView
            MouseEvent newEvent = new MouseEvent(
                MouseEvent.MOUSE_PRESSED,
                localPoint.getX(),
                localPoint.getY(),
                event.getScreenX(),
                event.getScreenY(),
                event.getButton(),
                event.getClickCount(),
                event.isShiftDown(),
                event.isControlDown(),
                event.isAltDown(),
                event.isMetaDown(),
                event.isPrimaryButtonDown(),
                event.isMiddleButtonDown(),
                event.isSecondaryButtonDown(),
                event.isSynthesized(),
                event.isPopupTrigger(),
                event.isStillSincePress(),
                event.getPickResult()
            );

            webView.fireEvent(newEvent);

            log.debug("Fired mouse event to WebView at local ({}, {})", localPoint.getX(), localPoint.getY());
            event.consume();
            return;
        }

        // Create new text element at click position with empty content
        TextElement element = new TextElement(
            UUID.randomUUID().toString(),
            event.getX(),
            event.getY(),
            DEFAULT_WIDTH,
            DEFAULT_HEIGHT,
            "",
            0  // Default z-index for text
        );

        log.debug("Created text element at ({}, {})", event.getX(), event.getY());

        // Notify listener
        if (onTextElementCreated != null) {
            onTextElementCreated.accept(element);
        }

        event.consume();
    }

    private javafx.scene.Node findTextNodeAt(double x, double y) {
        // Find text nodes (WebView) at the given coordinates
        var children = elementsPane.getChildren();

        for (int i = children.size() - 1; i >= 0; i--) {
            javafx.scene.Node node = children.get(i);

            // Only interested in WebView nodes (text elements)
            if (node instanceof javafx.scene.web.WebView) {
                if (node.contains(node.parentToLocal(x, y))) {
                    return node;
                }
            }
        }

        return null;
    }

    @Override
    protected void handleMouseDragged(MouseEvent event) {
        // No tool-specific drag behavior for text tool
    }

    @Override
    protected void handleMouseReleased(MouseEvent event) {
        // No tool-specific release behavior for text tool
    }

    @Override
    public void activate() {
        canvasContainer.setCursor(Cursor.TEXT);
        log.debug("Text tool activated");
    }

    @Override
    public void deactivate() {
        canvasContainer.setCursor(Cursor.DEFAULT);
        log.debug("Text tool deactivated");
    }

    @Override
    public String getName() {
        return "Text";
    }

    public void setOnTextElementCreated(Consumer<TextElement> listener) {
        this.onTextElementCreated = listener;
    }
}
