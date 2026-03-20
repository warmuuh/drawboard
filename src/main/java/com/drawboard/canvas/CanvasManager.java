package com.drawboard.canvas;

import com.drawboard.canvas.tools.HighlighterTool;
import com.drawboard.canvas.tools.PenTool;
import com.drawboard.canvas.tools.SelectionTool;
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
import java.util.function.Function;

/**
 * Manages the canvas rendering and coordinates all element renderers.
 * Uses a hybrid approach: JavaFX Canvas for drawings, JavaFX Nodes for text/images.
 */
public class CanvasManager {
    private static final Logger log = LoggerFactory.getLogger(CanvasManager.class);

    private final Pane canvasContainer;
    private final Pane elementsPane;
    private final Canvas overlayCanvas;

    private final TextElementRenderer textRenderer;
    private final ImageElementRenderer imageRenderer;
    private final DrawingElementRenderer drawingRenderer;

    private final ToolManager toolManager;

    // Map of element ID to its rendered node (for text and image elements)
    private final Map<String, javafx.scene.Node> elementNodes;

    private Page currentPage;

    // Listeners for element creation, updates, and deletion
    private Consumer<TextElement> onTextElementAdded;
    private Consumer<DrawingElement> onDrawingElementAdded;
    private Consumer<CanvasElement> onElementUpdated;
    private Consumer<String> onElementDeleted;

    // Function to load image data for rendering
    private Function<String, byte[]> imageDataLoader;

    public CanvasManager(Pane canvasContainer) {
        this.canvasContainer = canvasContainer;
        this.elementNodes = new HashMap<>();

        // Make container focusable to receive key events
        canvasContainer.setFocusTraversable(true);

        // Create elements pane for all elements (text, images, drawings)
        this.elementsPane = new Pane();
        this.elementsPane.setPrefSize(2000, 2000);
        this.elementsPane.setMinSize(0, 0); // Allow shrinking
        this.elementsPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // Create overlay canvas (top layer for active drawing)
        this.overlayCanvas = new Canvas(2000, 2000);
        // Make canvas mouse transparent so events pass through to the container
        this.overlayCanvas.setMouseTransparent(true);

        // Bind canvas size to pane size so they always match
        overlayCanvas.widthProperty().bind(elementsPane.widthProperty());
        overlayCanvas.heightProperty().bind(elementsPane.heightProperty());

        // Stack layers: elements pane (back) -> overlay canvas (front)
        canvasContainer.getChildren().addAll(elementsPane, overlayCanvas);

        // Initialize renderers
        this.textRenderer = new TextElementRenderer();
        this.imageRenderer = new ImageElementRenderer();
        this.drawingRenderer = new DrawingElementRenderer();

        // Set up text content change listener
        setupTextContentListener();

        // Set up WebView click listener for selection support
        setupWebViewClickListener();

        // Initialize tool manager with overlay canvas for active drawing
        this.toolManager = new ToolManager(canvasContainer, elementsPane, overlayCanvas);

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

    private void setupWebViewClickListener() {
        textRenderer.setOnWebViewClicked(webView -> {
            // When a WebView is clicked, select it using the SelectionTool
            SelectionTool selectionTool = (SelectionTool) toolManager.getTool("Selection");
            if (selectionTool != null) {
                selectionTool.selectNode(webView);
                log.debug("Selected WebView via click handler");
            }
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
                // Clear the overlay canvas after completing a drawing
                overlayCanvas.getGraphicsContext2D().clearRect(0, 0,
                    overlayCanvas.getWidth(), overlayCanvas.getHeight());

                if (onDrawingElementAdded != null) {
                    onDrawingElementAdded.accept(element);
                }
            });
        }

        // Highlighter tool listener
        HighlighterTool highlighterTool = (HighlighterTool) toolManager.getTool("Highlighter");
        if (highlighterTool != null) {
            highlighterTool.setOnDrawingComplete(element -> {
                // Clear the overlay canvas after completing a drawing
                overlayCanvas.getGraphicsContext2D().clearRect(0, 0,
                    overlayCanvas.getWidth(), overlayCanvas.getHeight());

                if (onDrawingElementAdded != null) {
                    onDrawingElementAdded.accept(element);
                }
            });
        }

        // Selection tool listener
        SelectionTool selectionTool = (SelectionTool) toolManager.getTool("Selection");
        if (selectionTool != null) {
            selectionTool.setOnElementMoved((node, newPosition) -> {
                // Find the element ID for this node
                String elementId = findElementIdForNode(node);
                if (elementId != null && currentPage != null) {
                    // Find and update the element
                    CanvasElement element = findElementById(elementId);
                    if (element != null) {
                        CanvasElement updated = updateElementPosition(element, newPosition.getX(), newPosition.getY());
                        if (onElementUpdated != null) {
                            onElementUpdated.accept(updated);
                        }
                    }
                }
            });

            selectionTool.setOnElementDeleted(node -> {
                // Find the element ID for this node
                String elementId = findElementIdForNode(node);
                if (elementId != null) {
                    // Remove from canvas
                    removeElement(elementId);

                    // Notify listener to delete from page
                    if (onElementDeleted != null) {
                        onElementDeleted.accept(elementId);
                    }

                    log.info("Deleted element: {}", elementId);
                }
            });
        }
    }

    private CanvasElement updateElementPosition(CanvasElement element, double newX, double newY) {
        // Also update size if the node has been resized
        javafx.scene.Node node = elementNodes.get(element.id());

        return switch (element) {
            case TextElement te -> {
                double width = te.width();
                double height = te.height();
                if (node instanceof javafx.scene.web.WebView webView) {
                    width = webView.getPrefWidth();
                    height = webView.getPrefHeight();
                }
                yield new TextElement(te.id(), newX, newY, width, height, te.htmlContent(), te.zIndex());
            }
            case ImageElement ie -> {
                double width = ie.width();
                double height = ie.height();
                if (node != null) {
                    width = node.getBoundsInLocal().getWidth();
                    height = node.getBoundsInLocal().getHeight();
                }
                yield new ImageElement(ie.id(), newX, newY, width, height, ie.filename(), ie.zIndex());
            }
            case DrawingElement de -> new DrawingElement(de.id(), newX, newY, de.paths(), de.zIndex());
        };
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
        javafx.scene.Node node;

        // Try to load actual image data
        if (imageDataLoader != null) {
            byte[] imageData = imageDataLoader.apply(element.filename());
            if (imageData != null) {
                node = imageRenderer.renderWithData(element, imageData);
                log.debug("Rendered image element: {} with actual data", element.filename());
            } else {
                log.warn("Failed to load image data for: {}, rendering placeholder", element.filename());
                node = imageRenderer.render(element);
            }
        } else {
            // No image loader configured, use placeholder
            node = imageRenderer.render(element);
            log.debug("Rendered image placeholder: {}", element.filename());
        }

        if (node != null) {
            elementNodes.put(element.id(), node);
            elementsPane.getChildren().add(node);
        }
    }

    private void renderDrawingElement(DrawingElement element) {
        javafx.scene.Node node = drawingRenderer.render(element);
        elementNodes.put(element.id(), node);

        // Add to pane - z-order is determined by insertion order
        // We rely on loadPage() sorting elements by z-index before rendering
        elementsPane.getChildren().add(node);

        log.debug("Rendered drawing element with {} paths at z-index {}", element.paths().size(), element.zIndex());
    }

    /**
     * Add a new element to the current page.
     */
    public void addElement(CanvasElement element) {
        if (currentPage == null) {
            log.warn("Cannot add element: no page loaded");
            return;
        }

        // Update currentPage to include the new element
        java.util.List<CanvasElement> updatedElements = new java.util.ArrayList<>(currentPage.elements());
        updatedElements.add(element);
        currentPage = new Page(
            currentPage.id(),
            currentPage.name(),
            currentPage.created(),
            currentPage.modified(),
            updatedElements
        );

        // Render the element
        javafx.scene.Node node = createNodeForElement(element);
        if (node != null) {
            elementNodes.put(element.id(), node);

            // Insert at correct z-order position
            int insertIndex = 0;
            for (javafx.scene.Node existingNode : elementsPane.getChildren()) {
                String existingId = findElementIdForNode(existingNode);
                if (existingId != null) {
                    CanvasElement existingElement = findElementById(existingId);
                    if (existingElement != null && existingElement.zIndex() >= element.zIndex()) {
                        break;
                    }
                }
                insertIndex++;
            }

            elementsPane.getChildren().add(insertIndex, node);
            log.debug("Added element at z-index {}, position {}", element.zIndex(), insertIndex);
        }
    }

    private javafx.scene.Node createNodeForElement(CanvasElement element) {
        javafx.scene.Node node = switch (element) {
            case TextElement te -> textRenderer.render(te);
            case ImageElement ie -> {
                if (imageDataLoader != null) {
                    byte[] imageData = imageDataLoader.apply(ie.filename());
                    if (imageData != null) {
                        yield imageRenderer.renderWithData(ie, imageData);
                    }
                }
                yield imageRenderer.render(ie);
            }
            case DrawingElement de -> drawingRenderer.render(de);
        };

        // If it's a new text element with empty content, focus it for immediate editing and select it
        if (element instanceof TextElement te && (te.htmlContent() == null || te.htmlContent().isEmpty())) {
            if (node instanceof javafx.scene.web.WebView webView) {
                javafx.application.Platform.runLater(() -> {
                    // Select the node first to show border and handles
                    SelectionTool selectionTool = (SelectionTool) toolManager.getTool("Selection");
                    if (selectionTool != null) {
                        selectionTool.selectNode(webView);
                    }

                    // Then focus for editing
                    webView.requestFocus();
                    // Focus the contentEditable body
                    try {
                        webView.getEngine().executeScript("if (document.body) document.body.focus();");
                    } catch (Exception e) {
                        log.debug("Could not focus new text element: {}", e.getMessage());
                    }
                });
            }
        }

        return node;
    }

    private String findElementIdForNode(javafx.scene.Node node) {
        return elementNodes.entrySet().stream()
            .filter(e -> e.getValue() == node)
            .map(java.util.Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    private CanvasElement findElementById(String id) {
        if (currentPage == null) return null;
        return currentPage.elements().stream()
            .filter(e -> e.id().equals(id))
            .findFirst()
            .orElse(null);
    }

    /**
     * Update an existing element.
     */
    public void updateElement(CanvasElement element) {
        // Find the existing node
        javafx.scene.Node node = elementNodes.get(element.id());

        if (node == null) {
            // Node doesn't exist, render it
            renderElement(element);
            return;
        }

        // Update the node's properties without removing/re-adding it
        // This preserves z-order
        switch (element) {
            case TextElement te -> {
                // For position changes, just update layout
                node.setLayoutX(te.x());
                node.setLayoutY(te.y());

                // For content changes, update WebView content
                if (node instanceof javafx.scene.web.WebView webView) {
                    String currentContent = (String) webView.getEngine().executeScript("document.body.innerHTML");
                    if (currentContent == null || !currentContent.equals(te.htmlContent())) {
                        webView.getEngine().loadContent(textRenderer.wrapHtmlContent(te.htmlContent()));
                    }
                }
            }
            case ImageElement ie -> {
                node.setLayoutX(ie.x());
                node.setLayoutY(ie.y());
                // TODO: update image if needed
            }
            case DrawingElement de -> {
                node.setLayoutX(de.x());
                node.setLayoutY(de.y());
                // Drawing paths can't be easily updated, might need re-render
            }
        }
    }

    /**
     * Remove an element from the canvas.
     */
    public void removeElement(String elementId) {
        removeElementNode(elementId);
    }

    private void removeElementNode(String elementId) {
        javafx.scene.Node node = elementNodes.remove(elementId);
        if (node != null) {
            elementsPane.getChildren().remove(node);
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

    /**
     * Set the background color for text elements.
     */
    public void setBackgroundColor(String backgroundColor) {
        textRenderer.setBackgroundColor(backgroundColor);

        // Re-render existing text elements with the new background color
        if (currentPage != null) {
            currentPage.elements().stream()
                .filter(e -> e instanceof TextElement)
                .forEach(element -> {
                    TextElement textElement = (TextElement) element;
                    javafx.scene.Node existingNode = elementNodes.get(textElement.id());
                    if (existingNode != null) {
                        elementsPane.getChildren().remove(existingNode);
                        javafx.scene.Node newNode = textRenderer.render(textElement);
                        elementsPane.getChildren().add(newNode);
                        elementNodes.put(textElement.id(), newNode);
                    }
                });
        }
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

    public void setOnElementDeleted(Consumer<String> listener) {
        this.onElementDeleted = listener;
    }

    /**
     * Set the image data loader function.
     * The function takes a filename and returns the image bytes.
     */
    public void setImageDataLoader(Function<String, byte[]> imageDataLoader) {
        this.imageDataLoader = imageDataLoader;
    }
}
