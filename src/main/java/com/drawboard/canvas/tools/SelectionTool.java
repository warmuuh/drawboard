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
    private Node selectedNode;
    private Rectangle selectionBorder;

    private double dragStartX;
    private double dragStartY;
    private double nodeStartX;
    private double nodeStartY;

    public SelectionTool(Pane canvasContainer) {
        this.canvasContainer = canvasContainer;
    }

    @Override
    public void onMousePressed(MouseEvent event) {
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

        event.consume();
    }

    @Override
    public void onMouseDragged(MouseEvent event) {
        if (selectedNode != null) {
            // Calculate drag delta
            double deltaX = event.getX() - dragStartX;
            double deltaY = event.getY() - dragStartY;

            // Move the selected node
            double newX = nodeStartX + deltaX;
            double newY = nodeStartY + deltaY;

            selectedNode.setLayoutX(newX);
            selectedNode.setLayoutY(newY);

            // Update selection border
            updateSelectionBorder();

            event.consume();
        }
    }

    @Override
    public void onMouseReleased(MouseEvent event) {
        if (selectedNode != null) {
            log.debug("Moved node to ({}, {})",
                selectedNode.getLayoutX(), selectedNode.getLayoutY());

            // TODO: Save the new position to the page model
        }

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
        log.debug("Selection tool activated");
    }

    @Override
    public void deactivate() {
        deselectNode();
        canvasContainer.setCursor(Cursor.DEFAULT);
        log.debug("Selection tool deactivated");
    }

    @Override
    public String getName() {
        return "Selection";
    }

    private Node findNodeAt(double x, double y) {
        // Find the topmost node at the given coordinates
        // Iterate in reverse order (top to bottom in z-order)
        var children = canvasContainer.getChildren();

        for (int i = children.size() - 1; i >= 0; i--) {
            Node node = children.get(i);

            // Skip the selection border itself
            if (node == selectionBorder) {
                continue;
            }

            // Skip the canvas (we only want to select nodes on top)
            if (node instanceof javafx.scene.canvas.Canvas) {
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

        // Bring selected node to front (for better visibility during drag)
        selectedNode.toFront();
        selectionBorder.toFront();
    }

    private void deselectNode() {
        if (selectedNode != null) {
            selectedNode = null;
        }

        if (selectionBorder != null) {
            canvasContainer.getChildren().remove(selectionBorder);
            selectionBorder = null;
        }
    }

    private void createSelectionBorder() {
        selectionBorder = new Rectangle();
        selectionBorder.setFill(Color.TRANSPARENT);
        selectionBorder.setStroke(Color.DODGERBLUE);
        selectionBorder.setStrokeWidth(2);
        selectionBorder.setMouseTransparent(true); // Don't intercept mouse events

        updateSelectionBorder();
        canvasContainer.getChildren().add(selectionBorder);
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

    public Node getSelectedNode() {
        return selectedNode;
    }

    public void clearSelection() {
        deselectNode();
    }
}
