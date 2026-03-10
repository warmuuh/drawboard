package com.drawboard.canvas.tools;

import javafx.scene.canvas.Canvas;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages canvas tools and dispatches mouse events to the active tool.
 */
public class ToolManager {
    private static final Logger log = LoggerFactory.getLogger(ToolManager.class);

    private final Map<String, Tool> tools;
    private Tool activeTool;

    public ToolManager(Pane canvasContainer, Canvas drawingCanvas) {
        this.tools = new HashMap<>();

        // Register available tools
        tools.put("Selection", new SelectionTool(canvasContainer));
        tools.put("Pen", new PenTool(canvasContainer, drawingCanvas));
        tools.put("Text", new TextTool(canvasContainer));

        // Set up mouse event handlers
        setupMouseHandlers(canvasContainer);

        // Default to selection tool
        setActiveTool("Selection");
    }

    private void setupMouseHandlers(Pane canvasContainer) {
        canvasContainer.setOnMousePressed(this::handleMousePressed);
        canvasContainer.setOnMouseDragged(this::handleMouseDragged);
        canvasContainer.setOnMouseReleased(this::handleMouseReleased);
        canvasContainer.setOnMouseMoved(this::handleMouseMoved);
    }

    public void setActiveTool(String toolName) {
        Tool newTool = tools.get(toolName);
        if (newTool == null) {
            log.warn("Tool not found: {}", toolName);
            return;
        }

        if (activeTool != null) {
            activeTool.deactivate();
        }

        activeTool = newTool;
        activeTool.activate();

        log.debug("Switched to tool: {}", toolName);
    }

    public Tool getActiveTool() {
        return activeTool;
    }

    public Tool getTool(String toolName) {
        return tools.get(toolName);
    }

    private void handleMousePressed(MouseEvent event) {
        if (activeTool != null) {
            activeTool.onMousePressed(event);
        }
    }

    private void handleMouseDragged(MouseEvent event) {
        if (activeTool != null) {
            activeTool.onMouseDragged(event);
        }
    }

    private void handleMouseReleased(MouseEvent event) {
        if (activeTool != null) {
            activeTool.onMouseReleased(event);
        }
    }

    private void handleMouseMoved(MouseEvent event) {
        if (activeTool != null) {
            activeTool.onMouseMoved(event);
        }
    }
}
