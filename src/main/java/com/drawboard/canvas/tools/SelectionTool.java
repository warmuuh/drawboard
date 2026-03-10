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
public class SelectionTool implements Tool {
    private static final Logger log = LoggerFactory.getLogger(SelectionTool.class);

    private final Pane canvasContainer;
    private final Pane elementsPane;
    private Node selectedNode;
    private Rectangle selectionBorder;
    private javafx.scene.Group resizeHandles;

    private double dragStartX;
    private double dragStartY;
    private double nodeStartX;
    private double nodeStartY;
    private double nodeStartWidth;
    private double nodeStartHeight;

    private ResizeHandle activeResizeHandle;

    private java.util.function.BiConsumer<Node, javafx.geometry.Point2D> onElementMoved;
    private java.util.function.Consumer<Node> onElementDeleted;

    private enum ResizeHandle {
        NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
        TOP, BOTTOM, LEFT, RIGHT
    }

    public SelectionTool(Pane canvasContainer, Pane elementsPane) {
        this.canvasContainer = canvasContainer;
        this.elementsPane = elementsPane;
    }

    @Override
    public void onMousePressed(MouseEvent event) {
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

            if (clickedNode != null && clickedNode != selectionBorder) {
                selectNode(clickedNode);

                // Start drag
                dragStartX = event.getX();
                dragStartY = event.getY();
                nodeStartX = clickedNode.getLayoutX();
                nodeStartY = clickedNode.getLayoutY();

                log.debug("Selected node at ({}, {})", nodeStartX, nodeStartY);
            } else {
                deselectNode();
            }
        }

        event.consume();
    }

    @Override
    public void onMouseDragged(MouseEvent event) {
        if (selectedNode == null) {
            return;
        }

        double deltaX = event.getX() - dragStartX;
        double deltaY = event.getY() - dragStartY;

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
        updateSelectionBorder();
        updateResizeHandles();

        event.consume();
    }

    private void handleResize(double deltaX, double deltaY) {
        if (!(selectedNode instanceof javafx.scene.web.WebView webView)) {
            return; // Only support resizing WebViews (text elements) for now
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
        webView.setPrefWidth(newWidth);
        webView.setPrefHeight(newHeight);
    }

    @Override
    public void onMouseReleased(MouseEvent event) {
        if (selectedNode != null && (activeResizeHandle != ResizeHandle.NONE ||
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
            if (selectedNode != null &&
                (event.getCode() == javafx.scene.input.KeyCode.DELETE ||
                 event.getCode() == javafx.scene.input.KeyCode.BACK_SPACE)) {

                // Notify listener to delete the element
                if (onElementDeleted != null) {
                    onElementDeleted.accept(selectedNode);
                }

                // Deselect after deletion
                deselectNode();

                event.consume();
            }
        });

        log.debug("Selection tool activated");
    }

    @Override
    public void deactivate() {
        deselectNode();
        canvasContainer.setCursor(Cursor.DEFAULT);
        canvasContainer.setOnKeyPressed(null); // Remove key handler
        log.debug("Selection tool deactivated");
    }

    @Override
    public String getName() {
        return "Selection";
    }

    private Node findNodeAt(double x, double y) {
        // Find the topmost node at the given coordinates
        // Iterate in reverse order (top to bottom in z-order)
        var children = elementsPane.getChildren();

        for (int i = children.size() - 1; i >= 0; i--) {
            Node node = children.get(i);

            // Skip the selection border itself
            if (node == selectionBorder) {
                continue;
            }

            // Check if point is within node bounds
            if (node.contains(node.parentToLocal(x, y))) {
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

        // Position border around selected node
        selectionBorder.setX(selectedNode.getLayoutX() - 2);
        selectionBorder.setY(selectedNode.getLayoutY() - 2);
        selectionBorder.setWidth(selectedNode.getBoundsInParent().getWidth() + 4);
        selectionBorder.setHeight(selectedNode.getBoundsInParent().getHeight() + 4);
    }

    private void updateResizeHandles() {
        if (resizeHandles == null || selectedNode == null) {
            return;
        }

        double x = selectedNode.getLayoutX();
        double y = selectedNode.getLayoutY();
        double width = selectedNode.getBoundsInParent().getWidth();
        double height = selectedNode.getBoundsInParent().getHeight();
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
        deselectNode();
    }

    public void setOnElementMoved(java.util.function.BiConsumer<Node, javafx.geometry.Point2D> listener) {
        this.onElementMoved = listener;
    }

    public void setOnElementDeleted(java.util.function.Consumer<Node> listener) {
        this.onElementDeleted = listener;
    }
}
