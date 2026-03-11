package com.drawboard.canvas.tools;

import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for tools that provides common functionality like canvas panning.
 * All tools can pan the canvas by middle-clicking and dragging.
 */
public abstract class AbstractTool implements Tool {
    private static final Logger log = LoggerFactory.getLogger(AbstractTool.class);

    protected final Pane canvasContainer;
    protected final Pane elementsPane;

    // Canvas panning state (shared by all tools)
    private boolean isPanning;
    private double panStartX;
    private double panStartY;
    private double canvasStartTranslateX;
    private double canvasStartTranslateY;

    protected AbstractTool(Pane canvasContainer, Pane elementsPane) {
        this.canvasContainer = canvasContainer;
        this.elementsPane = elementsPane;
    }

    @Override
    public final void onMousePressed(MouseEvent event) {
        // Check for middle button pan first (common to all tools)
        if (event.isMiddleButtonDown()) {
            startPanning(event);
            event.consume();
            return;
        }

        // Delegate to tool-specific implementation
        handleMousePressed(event);
    }

    @Override
    public final void onMouseDragged(MouseEvent event) {
        // Handle panning if active
        if (isPanning) {
            updatePanning(event);
            event.consume();
            return;
        }

        // Delegate to tool-specific implementation
        handleMouseDragged(event);
    }

    @Override
    public final void onMouseReleased(MouseEvent event) {
        // Handle panning if active
        if (isPanning) {
            stopPanning();
            event.consume();
            return;
        }

        // Delegate to tool-specific implementation
        handleMouseReleased(event);
    }

    // ==================== Panning Implementation ====================

    private void startPanning(MouseEvent event) {
        isPanning = true;
        panStartX = event.getX();
        panStartY = event.getY();
        canvasStartTranslateX = elementsPane.getTranslateX();
        canvasStartTranslateY = elementsPane.getTranslateY();
        canvasContainer.setCursor(Cursor.MOVE);
        log.debug("Starting canvas pan (middle button) in {}", getName());
    }

    private void updatePanning(MouseEvent event) {
        double deltaX = event.getX() - panStartX;
        double deltaY = event.getY() - panStartY;
        elementsPane.setTranslateX(canvasStartTranslateX + deltaX);
        elementsPane.setTranslateY(canvasStartTranslateY + deltaY);
    }

    private void stopPanning() {
        isPanning = false;
        canvasContainer.setCursor(Cursor.DEFAULT);
        log.debug("Canvas panned to ({}, {}) in {}",
            elementsPane.getTranslateX(), elementsPane.getTranslateY(), getName());
    }

    // ==================== Abstract Methods for Subclasses ====================

    /**
     * Handle mouse press event (tool-specific logic).
     * Override this instead of onMousePressed().
     */
    protected abstract void handleMousePressed(MouseEvent event);

    /**
     * Handle mouse drag event (tool-specific logic).
     * Override this instead of onMouseDragged().
     */
    protected abstract void handleMouseDragged(MouseEvent event);

    /**
     * Handle mouse release event (tool-specific logic).
     * Override this instead of onMouseReleased().
     */
    protected abstract void handleMouseReleased(MouseEvent event);
}
