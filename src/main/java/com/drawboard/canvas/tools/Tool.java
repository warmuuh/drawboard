package com.drawboard.canvas.tools;

import javafx.scene.input.MouseEvent;

/**
 * Base interface for all canvas tools.
 * Tools handle mouse interactions on the canvas.
 */
public interface Tool {

    /**
     * Handle mouse press event.
     */
    void onMousePressed(MouseEvent event);

    /**
     * Handle mouse drag event.
     */
    void onMouseDragged(MouseEvent event);

    /**
     * Handle mouse release event.
     */
    void onMouseReleased(MouseEvent event);

    /**
     * Handle mouse move event (without button pressed).
     */
    default void onMouseMoved(MouseEvent event) {
        // Optional - override if needed
    }

    /**
     * Activate the tool.
     */
    default void activate() {
        // Optional - override if needed
    }

    /**
     * Deactivate the tool.
     */
    default void deactivate() {
        // Optional - override if needed
    }

    /**
     * Get the tool name.
     */
    String getName();
}
