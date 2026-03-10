package com.drawboard.canvas;

import com.drawboard.canvas.tools.PenTool;
import com.drawboard.canvas.tools.TextTool;
import com.drawboard.canvas.tools.ToolManager;
import com.drawboard.domain.Page;
import com.drawboard.domain.elements.CanvasElement;
import com.drawboard.domain.elements.DrawingElement;
import com.drawboard.domain.elements.ImageElement;
import com.drawboard.domain.elements.TextElement;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Manages the canvas rendering and coordinates all element renderers.
 * Uses a hybrid approach: JavaFX Canvas for drawings, JavaFX Nodes for text/images.
 */
public class CanvasManager {
    private static final Logger log = LoggerFactory.getLogger(CanvasManager.class);

    private final Pane canvasContainer;
    private final Canvas drawingCanvas;
    private final Pane elementsPane;

    private final TextElementRenderer textRenderer;
    private final ImageElementRenderer imageRenderer;
    private final DrawingElementRenderer drawingRenderer;

    private final ToolManager toolManager;

    // Map of element ID to its rendered node (for text and image elements)
    private final Map<String, javafx.scene.Node> elementNodes;

    private Page currentPage;

    // Listeners for element creation and updates
    private Consumer<TextElement> onTextElementAdded;
    private Consumer<DrawingElement> onDrawingElementAdded;
    private Consumer<CanvasElement> onElementUpdated;

    public CanvasManager(Pane canvasContainer) {
        this.canvasContainer = canvasContainer;
        this.elementNodes = new HashMap<>();

        // Create drawing canvas (bottom layer)
        this.drawingCanvas = new Canvas(2000, 2000);

        // Create elements pane (top layer) for text and images
        this.elementsPane = new Pane();
        this.elementsPane.setPrefSize(2000, 2000);

        // Stack layers
        canvasContainer.getChildren().addAll(drawingCanvas, elementsPane);

        // Initialize renderers
        this.textRenderer = new TextElementRenderer();
        this.imageRenderer = new ImageElementRenderer();
        this.drawingRenderer = new DrawingElementRenderer(drawingCanvas.getGraphicsContext2D());

        // Set up text content change listener
        setupTextContentListener();

        // Initialize tool manager
        this.toolManager = new ToolManager(canvasContainer, drawingCanvas);

        // Set up tool listeners
        setupToolListeners();

        log.debug("Canvas manager initialized");
    }

    private void setupTextContentListener() {
        textRenderer.setOnContentChanged((elementId, newContent) -> {
            if (currentPage == null) return;

            // Find the element and create an updated version
            currentPage.elements().stream()
                .filter(e -> e.id().equals(elementId) && e instanceof TextElement)
                .findFirst()
                .ifPresent(element -> {
                    TextElement textElement = (TextElement) element;
                    TextElement updated = new TextElement(
                        textElement.id(),
                        textElement.x(),
                        textElement.y(),
                        textElement.width(),
                        textElement.height(),
                        newContent,
                        textElement.zIndex()
                    );

                    if (onElementUpdated != null) {
                        onElementUpdated.accept(updated);
                    }
                });
        });
    }

    private void setupToolListeners() {
        // Text tool listener
        TextTool textTool = (TextTool) toolManager.getTool("Text");
        if (textTool != null) {
            textTool.setOnTextElementCreated(element -> {
                if (onTextElementAdded != null) {
                    onTextElementAdded.accept(element);
                }
            });
        }

        // Pen tool listener
        PenTool penTool = (PenTool) toolManager.getTool("Pen");
        if (penTool != null) {
            penTool.setOnDrawingComplete(element -> {
                if (onDrawingElementAdded != null) {
                    onDrawingElementAdded.accept(element);
                }
            });
        }
    }

    /**
     * Load a page and render all its elements.
     */
    public void loadPage(Page page) {
        // Clear canvas first (this will set currentPage to null)
        clearCanvas();

        // Then set the new page
        this.currentPage = page;

        if (page == null) {
            return;
        }

        log.debug("Loading page with {} elements", page.elements().size());

        // Sort elements by z-index to render in correct order
        var sortedElements = page.elements().stream()
            .sorted((e1, e2) -> Integer.compare(e1.zIndex(), e2.zIndex()))
            .toList();

        for (CanvasElement element : sortedElements) {
            renderElement(element);
        }
    }

    /**
     * Render a single element on the canvas.
     */
    private void renderElement(CanvasElement element) {
        switch (element) {
            case TextElement te -> renderTextElement(te);
            case ImageElement ie -> renderImageElement(ie);
            case DrawingElement de -> renderDrawingElement(de);
        }
    }

    private void renderTextElement(TextElement element) {
        javafx.scene.Node node = textRenderer.render(element);
        elementNodes.put(element.id(), node);
        elementsPane.getChildren().add(node);
        log.debug("Rendered text element at ({}, {})", element.x(), element.y());
    }

    private void renderImageElement(ImageElement element) {
        javafx.scene.Node node = imageRenderer.render(element);
        if (node != null) {
            elementNodes.put(element.id(), node);
            elementsPane.getChildren().add(node);
            log.debug("Rendered image element: {}", element.filename());
        }
    }

    private void renderDrawingElement(DrawingElement element) {
        drawingRenderer.render(element);
        log.debug("Rendered drawing element with {} paths", element.paths().size());
    }

    /**
     * Add a new element to the current page.
     */
    public void addElement(CanvasElement element) {
        if (currentPage == null) {
            log.warn("Cannot add element: no page loaded");
            return;
        }

        renderElement(element);
    }

    /**
     * Update an existing element.
     */
    public void updateElement(CanvasElement element) {
        removeElementNode(element.id());
        renderElement(element);
    }

    /**
     * Remove an element from the canvas.
     */
    public void removeElement(String elementId) {
        removeElementNode(elementId);
        // For drawings, we need to redraw the entire canvas
        if (currentPage != null) {
            redrawDrawingCanvas();
        }
    }

    private void removeElementNode(String elementId) {
        javafx.scene.Node node = elementNodes.remove(elementId);
        if (node != null) {
            elementsPane.getChildren().remove(node);
        }
    }

    private void redrawDrawingCanvas() {
        // Clear and redraw all drawing elements
        drawingCanvas.getGraphicsContext2D().clearRect(0, 0,
            drawingCanvas.getWidth(), drawingCanvas.getHeight());

        if (currentPage != null) {
            currentPage.elements().stream()
                .filter(e -> e instanceof DrawingElement)
                .map(e -> (DrawingElement) e)
                .forEach(drawingRenderer::render);
        }
    }

    /**
     * Clear the entire canvas.
     */
    public void clear() {
        clearCanvas();
        currentPage = null;
    }

    private void clearCanvas() {
        elementsPane.getChildren().clear();
        elementNodes.clear();
        drawingCanvas.getGraphicsContext2D().clearRect(0, 0,
            drawingCanvas.getWidth(), drawingCanvas.getHeight());
    }

    public Page getCurrentPage() {
        return currentPage;
    }

    public Pane getCanvasContainer() {
        return canvasContainer;
    }

    public ToolManager getToolManager() {
        return toolManager;
    }

    public void setOnTextElementAdded(Consumer<TextElement> listener) {
        this.onTextElementAdded = listener;
    }

    public void setOnDrawingElementAdded(Consumer<DrawingElement> listener) {
        this.onDrawingElementAdded = listener;
    }

    public void setOnElementUpdated(Consumer<CanvasElement> listener) {
        this.onElementUpdated = listener;
    }
}
