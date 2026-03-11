package com.drawboard.canvas.tools;

import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Selection tool for selecting and moving canvas elements.
 * Allows clicking on elements to select them and dragging to move them.
 */
public class SelectionTool extends AbstractTool {
    private static final Logger log = LoggerFactory.getLogger(SelectionTool.class);
    private Node selectedNode;
    private Rectangle selectionBorder;
    private javafx.scene.Group resizeHandles;
    private java.util.Map<Node, Rectangle> selectionBorders; // For multi-selection

    private double dragStartX;
    private double dragStartY;
    private double nodeStartX;
    private double nodeStartY;
    private double nodeStartWidth;
    private double nodeStartHeight;

    private ResizeHandle activeResizeHandle;

    private boolean isDrawingSelectionRect;
    private Rectangle selectionRect;
    private double selectionStartX;
    private double selectionStartY;
    private java.util.List<Node> selectedNodes;

    private java.util.function.BiConsumer<Node, javafx.geometry.Point2D> onElementMoved;
    private java.util.function.Consumer<Node> onElementDeleted;

    private enum ResizeHandle {
        NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
        TOP, BOTTOM, LEFT, RIGHT
    }

    public SelectionTool(Pane canvasContainer, Pane elementsPane) {
        super(canvasContainer, elementsPane);
        this.selectedNodes = new java.util.ArrayList<>();
        this.selectionBorders = new java.util.HashMap<>();
    }

    @Override
    protected void handleMousePressed(MouseEvent event) {
        // Primary button - selection/resize/drag
        if (!event.isPrimaryButtonDown()) {
            return;
        }

        // Check if clicking on a resize handle
        activeResizeHandle = getResizeHandleAt(event.getX(), event.getY());

        if (activeResizeHandle != ResizeHandle.NONE && selectedNode != null) {
            // Start resize
            dragStartX = event.getX();
            dragStartY = event.getY();
            nodeStartX = selectedNode.getLayoutX();
            nodeStartY = selectedNode.getLayoutY();
            nodeStartWidth = selectedNode.getBoundsInParent().getWidth();
            nodeStartHeight = selectedNode.getBoundsInParent().getHeight();

            log.debug("Starting resize from handle: {}", activeResizeHandle);
        } else {
            // Check if clicking on an existing node
            Node clickedNode = findNodeAt(event.getX(), event.getY());

            if (clickedNode != null && clickedNode != selectionRect && !selectionBorders.containsValue(clickedNode)) {
                // Select single node
                deselectAllNodes();
                selectedNodes.add(clickedNode);
                selectedNode = clickedNode;

                // Show selection border (for single selection, also show handles)
                showSelectionBorder(clickedNode);
                createResizeHandles();

                // Request focus so we can receive key events
                canvasContainer.requestFocus();

                // Start drag
                dragStartX = event.getX();
                dragStartY = event.getY();
                nodeStartX = clickedNode.getLayoutX();
                nodeStartY = clickedNode.getLayoutY();

                log.debug("Selected {} node(s)", selectedNodes.size());
            } else {
                // Clicked on empty space - start selection rectangle
                deselectAllNodes();
                isDrawingSelectionRect = true;

                // Adjust for canvas translation
                double translateX = elementsPane.getTranslateX();
                double translateY = elementsPane.getTranslateY();
                selectionStartX = event.getX() - translateX;
                selectionStartY = event.getY() - translateY;

                // Create selection rectangle
                selectionRect = new Rectangle();
                selectionRect.setFill(Color.rgb(33, 150, 243, 0.2)); // Light blue
                selectionRect.setStroke(Color.rgb(33, 150, 243, 0.8));
                selectionRect.setStrokeWidth(1);
                selectionRect.getStrokeDashArray().addAll(5.0, 5.0);
                selectionRect.setMouseTransparent(true);

                // Add to elements pane so it moves with the canvas
                elementsPane.getChildren().add(selectionRect);

                log.debug("Starting selection rectangle at ({}, {}) with translate ({}, {})",
                    selectionStartX, selectionStartY, translateX, translateY);
            }
        }

        event.consume();
    }

    @Override
    protected void handleMouseDragged(MouseEvent event) {
        double deltaX = event.getX() - dragStartX;
        double deltaY = event.getY() - dragStartY;

        if (isDrawingSelectionRect) {
            // Update selection rectangle
            // Adjust current position for canvas translation
            double translateX = elementsPane.getTranslateX();
            double translateY = elementsPane.getTranslateY();
            double currentX = event.getX() - translateX;
            double currentY = event.getY() - translateY;

            double x = Math.min(selectionStartX, currentX);
            double y = Math.min(selectionStartY, currentY);
            double width = Math.abs(currentX - selectionStartX);
            double height = Math.abs(currentY - selectionStartY);

            selectionRect.setX(x);
            selectionRect.setY(y);
            selectionRect.setWidth(width);
            selectionRect.setHeight(height);
        } else if (selectedNode != null) {
            if (activeResizeHandle != ResizeHandle.NONE) {
                // Resize the node
                handleResize(deltaX, deltaY);
            } else {
                // Move the selected node
                double newX = nodeStartX + deltaX;
                double newY = nodeStartY + deltaY;

                selectedNode.setLayoutX(newX);
                selectedNode.setLayoutY(newY);
            }

            // Update selection border and handles
            if (selectionBorders.containsKey(selectedNode)) {
                updateSelectionBorderForNode(selectedNode);
            }
            updateResizeHandles();
        }

        event.consume();
    }

    private void handleResize(double deltaX, double deltaY) {
        // Support resizing for WebViews (text elements) and ImageViews (image elements)
        if (!(selectedNode instanceof javafx.scene.web.WebView) &&
            !(selectedNode instanceof javafx.scene.image.ImageView)) {
            return;
        }

        double newX = nodeStartX;
        double newY = nodeStartY;
        double newWidth = nodeStartWidth;
        double newHeight = nodeStartHeight;

        switch (activeResizeHandle) {
            case BOTTOM_RIGHT -> {
                newWidth = nodeStartWidth + deltaX;
                newHeight = nodeStartHeight + deltaY;
            }
            case BOTTOM_LEFT -> {
                newX = nodeStartX + deltaX;
                newWidth = nodeStartWidth - deltaX;
                newHeight = nodeStartHeight + deltaY;
            }
            case TOP_RIGHT -> {
                newY = nodeStartY + deltaY;
                newWidth = nodeStartWidth + deltaX;
                newHeight = nodeStartHeight - deltaY;
            }
            case TOP_LEFT -> {
                newX = nodeStartX + deltaX;
                newY = nodeStartY + deltaY;
                newWidth = nodeStartWidth - deltaX;
                newHeight = nodeStartHeight - deltaY;
            }
            case RIGHT -> {
                newWidth = nodeStartWidth + deltaX;
            }
            case LEFT -> {
                newX = nodeStartX + deltaX;
                newWidth = nodeStartWidth - deltaX;
            }
            case BOTTOM -> {
                newHeight = nodeStartHeight + deltaY;
            }
            case TOP -> {
                newY = nodeStartY + deltaY;
                newHeight = nodeStartHeight - deltaY;
            }
        }

        // Apply minimum size constraints
        newWidth = Math.max(50, newWidth);
        newHeight = Math.max(30, newHeight);

        selectedNode.setLayoutX(newX);
        selectedNode.setLayoutY(newY);

        // Apply new size based on node type
        if (selectedNode instanceof javafx.scene.web.WebView webView) {
            webView.setPrefWidth(newWidth);
            webView.setPrefHeight(newHeight);
        } else if (selectedNode instanceof javafx.scene.image.ImageView imageView) {
            imageView.setFitWidth(newWidth);
            imageView.setFitHeight(newHeight);
        }
    }

    @Override
    protected void handleMouseReleased(MouseEvent event) {
        if (isDrawingSelectionRect) {
            // Complete selection - find all nodes within rectangle
            isDrawingSelectionRect = false;
            selectNodesInRectangle(selectionRect.getBoundsInParent());

            // Remove the selection rectangle
            elementsPane.getChildren().remove(selectionRect);
            selectionRect = null;

            log.debug("Selection complete: {} nodes selected", selectedNodes.size());
        } else if (selectedNode != null && (activeResizeHandle != ResizeHandle.NONE ||
            (Math.abs(event.getX() - dragStartX) > 1 || Math.abs(event.getY() - dragStartY) > 1))) {

            double newX = selectedNode.getLayoutX();
            double newY = selectedNode.getLayoutY();

            log.debug("Moved/resized node to ({}, {})", newX, newY);

            // Notify listener to save the new position/size
            if (onElementMoved != null) {
                onElementMoved.accept(selectedNode, new javafx.geometry.Point2D(newX, newY));
            }
        }

        activeResizeHandle = ResizeHandle.NONE;
        event.consume();
    }

    @Override
    public void onMouseMoved(MouseEvent event) {
        // Change cursor when hovering over selectable elements
        Node hoveredNode = findNodeAt(event.getX(), event.getY());

        if (hoveredNode != null && hoveredNode != selectionBorder) {
            canvasContainer.setCursor(Cursor.HAND);
        } else {
            canvasContainer.setCursor(Cursor.DEFAULT);
        }
    }

    @Override
    public void activate() {
        canvasContainer.setCursor(Cursor.DEFAULT);

        // Set up key handler for deletion
        canvasContainer.setOnKeyPressed(event -> {
            log.debug("Key pressed: {}, selectedNodes: {}", event.getCode(), selectedNodes.size());
            if (!selectedNodes.isEmpty() &&
                (event.getCode() == javafx.scene.input.KeyCode.DELETE ||
                 event.getCode() == javafx.scene.input.KeyCode.BACK_SPACE)) {

                log.debug("Deleting {} selected node(s)", selectedNodes.size());

                // Delete all selected nodes
                if (onElementDeleted != null) {
                    for (Node node : new java.util.ArrayList<>(selectedNodes)) {
                        onElementDeleted.accept(node);
                    }
                }

                // Deselect after deletion
                deselectAllNodes();

                event.consume();
            }
        });

        // Add background click handler to container for deselecting
        canvasContainer.setOnMouseClicked(event -> {
            // Only handle clicks directly on the container (not on child nodes)
            if (event.getTarget() == canvasContainer) {
                log.debug("Clicked on canvas background - deselecting all");
                deselectAllNodes();
                canvasContainer.requestFocus();
                event.consume();
            }
        });

        log.debug("Selection tool activated");
    }

    @Override
    public void deactivate() {
        deselectAllNodes();
        canvasContainer.setCursor(Cursor.DEFAULT);
        canvasContainer.setOnKeyPressed(null); // Remove key handler
        canvasContainer.setOnMouseClicked(null); // Remove background click handler

        log.debug("Selection tool deactivated");
    }

    private void selectNodesInRectangle(javafx.geometry.Bounds rectBounds) {
        deselectAllNodes();

        for (Node node : elementsPane.getChildren()) {
            // Skip selection visual elements and borders
            if (selectionBorders.containsValue(node) || node == selectionRect) {
                continue;
            }

            // Get node bounds (already in elementsPane coordinates)
            javafx.geometry.Bounds nodeBounds = node.getBoundsInParent();

            // Check if node intersects with selection rectangle
            if (rectBounds.intersects(nodeBounds)) {
                selectedNodes.add(node);
            }
        }

        // Show selection borders for all selected nodes
        for (Node node : selectedNodes) {
            showSelectionBorder(node);
        }

        // If single node selected, show resize handles
        if (selectedNodes.size() == 1) {
            selectedNode = selectedNodes.get(0);
            createResizeHandles();
        } else if (!selectedNodes.isEmpty()) {
            // Multiple nodes - set primary but don't show handles
            selectedNode = selectedNodes.get(0);
        }

        // Request focus so we can receive key events
        if (!selectedNodes.isEmpty()) {
            canvasContainer.requestFocus();
            log.debug("Selected {} node(s)", selectedNodes.size());
        }
    }

    private void showSelectionBorder(Node node) {
        Rectangle border = new Rectangle();
        border.setFill(Color.TRANSPARENT);
        border.setStroke(Color.DODGERBLUE);
        border.setStrokeWidth(2);
        border.getStrokeDashArray().addAll(5.0, 5.0);
        border.setMouseTransparent(true);

        javafx.geometry.Bounds bounds = node.getBoundsInParent();
        border.setX(bounds.getMinX() - 2);
        border.setY(bounds.getMinY() - 2);
        border.setWidth(bounds.getWidth() + 4);
        border.setHeight(bounds.getHeight() + 4);

        elementsPane.getChildren().add(border);
        selectionBorders.put(node, border);
    }

    private void updateSelectionBorderForNode(Node node) {
        Rectangle border = selectionBorders.get(node);
        if (border == null) {
            return;
        }

        javafx.geometry.Bounds bounds = node.getBoundsInParent();
        border.setX(bounds.getMinX() - 2);
        border.setY(bounds.getMinY() - 2);
        border.setWidth(bounds.getWidth() + 4);
        border.setHeight(bounds.getHeight() + 4);
    }

    private void deselectAllNodes() {
        // Check if we need to remove focus from WebView
        boolean hadWebView = false;
        for (Node node : selectedNodes) {
            if (node instanceof javafx.scene.web.WebView) {
                hadWebView = true;
                break;
            }
        }

        // Remove all selection borders
        for (Rectangle border : selectionBorders.values()) {
            elementsPane.getChildren().remove(border);
        }
        selectionBorders.clear();
        selectedNodes.clear();

        // Remove resize handles
        if (resizeHandles != null) {
            elementsPane.getChildren().remove(resizeHandles);
            resizeHandles = null;
        }

        // Clear primary selection
        selectedNode = null;

        // Only request focus if we had a WebView selected (to remove its internal focus)
        // or if explicitly needed for paste operations
        if (hadWebView) {
            canvasContainer.requestFocus();
            log.debug("Removed focus from WebView");
        }
    }

    @Override
    public String getName() {
        return "Selection";
    }

    private Node findNodeAt(double x, double y) {
        // Find the topmost node at the given coordinates
        // Account for canvas translation
        double translateX = elementsPane.getTranslateX();
        double translateY = elementsPane.getTranslateY();
        double adjustedX = x - translateX;
        double adjustedY = y - translateY;

        // Iterate in reverse order (top to bottom in z-order)
        var children = elementsPane.getChildren();

        for (int i = children.size() - 1; i >= 0; i--) {
            Node node = children.get(i);

            // Skip the selection border itself
            if (node == selectionBorder) {
                continue;
            }

            // Skip mouse-transparent nodes (they shouldn't be selectable)
            if (node.isMouseTransparent()) {
                continue;
            }

            // Check if point is within node bounds
            if (node.contains(node.parentToLocal(adjustedX, adjustedY))) {
                return node;
            }
        }

        return null;
    }

    private void selectNode(Node node) {
        if (selectedNode == node) {
            return; // Already selected
        }

        deselectNode();

        selectedNode = node;
        createSelectionBorder();

        // Don't call toFront() - it breaks z-index ordering
        // Just ensure selection border is on top
        selectionBorder.toFront();

        // Request focus on container so it can receive key events
        canvasContainer.requestFocus();
    }

    private void deselectNode() {
        if (selectedNode != null) {
            selectedNode = null;
        }

        if (selectionBorder != null) {
            elementsPane.getChildren().remove(selectionBorder);
            selectionBorder = null;
        }

        if (resizeHandles != null) {
            elementsPane.getChildren().remove(resizeHandles);
            resizeHandles = null;
        }
    }

    private void createSelectionBorder() {
        selectionBorder = new Rectangle();
        selectionBorder.setFill(Color.TRANSPARENT);
        selectionBorder.setStroke(Color.DODGERBLUE);
        selectionBorder.setStrokeWidth(2);
        selectionBorder.setMouseTransparent(true); // Don't intercept mouse events

        updateSelectionBorder();
        elementsPane.getChildren().add(selectionBorder);
        selectionBorder.toFront(); // Ensure it's on top

        // Create resize handles
        createResizeHandles();
    }

    private void createResizeHandles() {
        resizeHandles = new javafx.scene.Group();

        // Create 8 resize handles (4 corners + 4 edges)
        double handleSize = 8;

        for (ResizeHandle handle : new ResizeHandle[]{
            ResizeHandle.TOP_LEFT, ResizeHandle.TOP, ResizeHandle.TOP_RIGHT,
            ResizeHandle.LEFT, ResizeHandle.RIGHT,
            ResizeHandle.BOTTOM_LEFT, ResizeHandle.BOTTOM, ResizeHandle.BOTTOM_RIGHT
        }) {
            Rectangle handleRect = new Rectangle(handleSize, handleSize, Color.WHITE);
            handleRect.setStroke(Color.DODGERBLUE);
            handleRect.setStrokeWidth(1);
            handleRect.setUserData(handle);

            // Set cursor based on handle position
            handleRect.setCursor(getCursorForHandle(handle));

            resizeHandles.getChildren().add(handleRect);
        }

        updateResizeHandles();
        elementsPane.getChildren().add(resizeHandles);
        resizeHandles.toFront(); // Ensure handles are on top
    }

    private Cursor getCursorForHandle(ResizeHandle handle) {
        return switch (handle) {
            case TOP_LEFT, BOTTOM_RIGHT -> Cursor.NW_RESIZE;
            case TOP_RIGHT, BOTTOM_LEFT -> Cursor.NE_RESIZE;
            case TOP, BOTTOM -> Cursor.V_RESIZE;
            case LEFT, RIGHT -> Cursor.H_RESIZE;
            default -> Cursor.DEFAULT;
        };
    }

    private void updateSelectionBorder() {
        if (selectionBorder == null || selectedNode == null) {
            return;
        }

        // Use bounds in parent which gives us the actual visual position
        javafx.geometry.Bounds bounds = selectedNode.getBoundsInParent();

        selectionBorder.setX(bounds.getMinX() - 2);
        selectionBorder.setY(bounds.getMinY() - 2);
        selectionBorder.setWidth(bounds.getWidth() + 4);
        selectionBorder.setHeight(bounds.getHeight() + 4);
    }

    private void updateResizeHandles() {
        if (resizeHandles == null || selectedNode == null) {
            return;
        }

        // Use bounds in parent for consistency
        javafx.geometry.Bounds bounds = selectedNode.getBoundsInParent();
        double x = bounds.getMinX();
        double y = bounds.getMinY();
        double width = bounds.getWidth();
        double height = bounds.getHeight();
        double handleSize = 8;
        double offset = handleSize / 2;

        int i = 0;
        for (ResizeHandle handle : new ResizeHandle[]{
            ResizeHandle.TOP_LEFT, ResizeHandle.TOP, ResizeHandle.TOP_RIGHT,
            ResizeHandle.LEFT, ResizeHandle.RIGHT,
            ResizeHandle.BOTTOM_LEFT, ResizeHandle.BOTTOM, ResizeHandle.BOTTOM_RIGHT
        }) {
            Rectangle handleRect = (Rectangle) resizeHandles.getChildren().get(i++);

            double hx = switch (handle) {
                case TOP_LEFT, LEFT, BOTTOM_LEFT -> x - offset;
                case TOP, BOTTOM -> x + width / 2 - offset;
                case TOP_RIGHT, RIGHT, BOTTOM_RIGHT -> x + width - offset;
                default -> x;
            };

            double hy = switch (handle) {
                case TOP_LEFT, TOP, TOP_RIGHT -> y - offset;
                case LEFT, RIGHT -> y + height / 2 - offset;
                case BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT -> y + height - offset;
                default -> y;
            };

            handleRect.setX(hx);
            handleRect.setY(hy);
        }
    }

    private ResizeHandle getResizeHandleAt(double x, double y) {
        if (resizeHandles == null) {
            return ResizeHandle.NONE;
        }

        // Resize handles are positioned in container coordinates (already accounting for translation)
        // So we can directly test against them
        for (javafx.scene.Node node : resizeHandles.getChildren()) {
            if (node instanceof Rectangle rect && rect.contains(rect.parentToLocal(x, y))) {
                return (ResizeHandle) rect.getUserData();
            }
        }

        return ResizeHandle.NONE;
    }

    public Node getSelectedNode() {
        return selectedNode;
    }

    public void clearSelection() {
        deselectAllNodes();
    }

    public void setOnElementMoved(java.util.function.BiConsumer<Node, javafx.geometry.Point2D> listener) {
        this.onElementMoved = listener;
    }

    public void setOnElementDeleted(java.util.function.Consumer<Node> listener) {
        this.onElementDeleted = listener;
    }
}
