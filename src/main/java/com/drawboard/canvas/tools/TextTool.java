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
    private SelectionTool selectionTool;

    private static final double DEFAULT_WIDTH = 300;
    private static final double DEFAULT_HEIGHT = 100;

    public TextTool(Pane canvasContainer, Pane elementsPane) {
        super(canvasContainer, elementsPane);
    }

    public void setSelectionTool(SelectionTool selectionTool) {
        this.selectionTool = selectionTool;
    }

    @Override
    protected void handleMousePressed(MouseEvent event) {
        double translateX = elementsPane.getTranslateX();
        double translateY = elementsPane.getTranslateY();
        log.debug("TextTool handleMousePressed at ({}, {}) with translate ({}, {})",
            event.getX(), event.getY(), translateX, translateY);

        // Check if clicking on an existing text element
        javafx.scene.Node clickedNode = findTextNodeAt(event.getX(), event.getY());
        log.debug("findTextNodeAt returned: {}", clickedNode);

        if (clickedNode instanceof javafx.scene.web.WebView webView) {
            // Clicking on existing WebView - select it and focus it for editing

            // Select the node to show blue border and resize handles
            if (selectionTool != null) {
                log.debug("Selecting WebView node via SelectionTool");
                selectionTool.selectNode(webView);
            } else {
                log.warn("SelectionTool is null - cannot select node");
            }

            // Focus the WebView for text editing
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

            log.debug("Selected and fired mouse event to WebView at local ({}, {})", localPoint.getX(), localPoint.getY());
            event.consume();
            return;
        }

        // Create new text element at click position with empty content
        // Adjust for canvas translation (already calculated above)
        double adjustedX = event.getX() - translateX;
        double adjustedY = event.getY() - translateY;

        TextElement element = new TextElement(
            UUID.randomUUID().toString(),
            adjustedX,
            adjustedY,
            DEFAULT_WIDTH,
            DEFAULT_HEIGHT,
            "",
            0  // Default z-index for text
        );

        log.debug("Created text element at ({}, {}) with translate ({}, {})",
            adjustedX, adjustedY, translateX, translateY);

        // Notify listener
        if (onTextElementCreated != null) {
            onTextElementCreated.accept(element);
        }

        event.consume();
    }

    private javafx.scene.Node findTextNodeAt(double x, double y) {
        // Find text nodes (WebView) at the given coordinates
        // Account for canvas translation
        double translateX = elementsPane.getTranslateX();
        double translateY = elementsPane.getTranslateY();
        double adjustedX = x - translateX;
        double adjustedY = y - translateY;

        log.debug("findTextNodeAt: click at ({}, {}), adjusted to ({}, {})",
            x, y, adjustedX, adjustedY);

        var children = elementsPane.getChildren();

        for (int i = children.size() - 1; i >= 0; i--) {
            javafx.scene.Node node = children.get(i);

            // Only interested in WebView nodes (text elements)
            if (node instanceof javafx.scene.web.WebView) {
                if (node.contains(node.parentToLocal(adjustedX, adjustedY))) {
                    log.debug("Found WebView at layoutX={}, layoutY={}", node.getLayoutX(), node.getLayoutY());
                    return node;
                }
            }
        }

        log.debug("No WebView found at this position");
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
