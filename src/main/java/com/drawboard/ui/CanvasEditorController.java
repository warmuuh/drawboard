package com.drawboard.ui;

import com.drawboard.canvas.CanvasManager;
import com.drawboard.domain.Page;
import com.drawboard.domain.elements.ImageElement;
import com.drawboard.domain.elements.TextElement;
import com.drawboard.service.PageService;
import com.drawboard.service.PreferencesService;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.Pane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Controller for the canvas editor area.
 * Manages the canvas rendering and user interactions.
 */
public class CanvasEditorController {
    private static final Logger log = LoggerFactory.getLogger(CanvasEditorController.class);

    private final PageService pageService;
    private final PreferencesService preferencesService;
    private final CanvasManager canvasManager;

    private String currentNotebookId;
    private String currentChapterId;
    private String currentPageId;
    private String backgroundColor;
    private Runnable onToolSettingsChanged; // Callback to notify UI to refresh toolbar

    public CanvasEditorController(PageService pageService, PreferencesService preferencesService, Pane canvasContainer) {
        this.pageService = pageService;
        this.preferencesService = preferencesService;
        this.canvasManager = new CanvasManager(canvasContainer);

        // Set up listeners for new elements
        setupElementListeners();

        // Set up image data loader
        setupImageDataLoader();

        // Set up tool settings change listener
        setupToolSettingsListener();
    }

    private void setupElementListeners() {
        // Listen for text elements created by text tool
        canvasManager.setOnTextElementAdded(element -> {
            if (currentPageId != null) {
                pageService.addElement(currentNotebookId, currentChapterId, currentPageId, element);
                canvasManager.addElement(element);
                log.info("Added text element to page");
            }
        });

        // Listen for drawing elements created by pen tool
        canvasManager.setOnDrawingElementAdded(element -> {
            if (currentPageId != null) {
                pageService.addElement(currentNotebookId, currentChapterId, currentPageId, element);
                canvasManager.addElement(element);
                log.info("Added drawing element to page");
            }
        });

        // Listen for element updates (e.g., text content changes)
        canvasManager.setOnElementUpdated(element -> {
            if (currentPageId != null) {
                pageService.updateElement(currentNotebookId, currentChapterId, currentPageId, element);
                log.debug("Updated element: {}", element.id());
            }
        });

        // Listen for element deletion
        canvasManager.setOnElementDeleted(elementId -> {
            if (currentPageId != null) {
                pageService.deleteElement(currentNotebookId, currentChapterId, currentPageId, elementId);
                log.info("Deleted element: {}", elementId);
            }
        });
    }

    private void setupImageDataLoader() {
        canvasManager.setImageDataLoader(filename -> {
            if (currentNotebookId != null && currentChapterId != null && currentPageId != null) {
                return pageService.getImageData(currentNotebookId, currentChapterId, currentPageId, filename);
            }
            return null;
        });
    }

    private void setupToolSettingsListener() {
        canvasManager.setOnToolSettingsChanged(toolName -> {
            if (onToolSettingsChanged != null) {
                onToolSettingsChanged.run();
            }
        });
    }

    /**
     * Load and display a page on the canvas.
     */
    public void loadPage(String notebookId, String chapterId, String pageId) {
        this.currentNotebookId = notebookId;
        this.currentChapterId = chapterId;
        this.currentPageId = pageId;

        Page page = pageService.getPage(notebookId, chapterId, pageId);
        if (page != null) {
            canvasManager.loadPage(page);

            // Save as last opened page
            preferencesService.saveLastOpenedPage(notebookId, chapterId, pageId);

            log.info("Loaded page: {} with {} elements", page.name(), page.elements().size());
        } else {
            log.warn("Page not found: {}", pageId);
            canvasManager.clear();
        }
    }

    /**
     * Clear the canvas.
     */
    public void clear() {
        canvasManager.clear();
        currentNotebookId = null;
        currentChapterId = null;
        currentPageId = null;
    }

    public CanvasManager getCanvasManager() {
        return canvasManager;
    }

    public boolean hasPageLoaded() {
        return currentPageId != null;
    }

    public String getCurrentPageId() {
        return currentPageId;
    }

    /**
     * Set the active tool for canvas interaction.
     */
    public void setActiveTool(String toolName) {
        canvasManager.getToolManager().setActiveTool(toolName);
    }

    /**
     * Get the settings nodes for the current active tool.
     */
    public Optional<List<Node>> getCurrentToolSettings() {
        return canvasManager.getToolManager().getActiveToolSettings();
    }

    /**
     * Handle paste operation from clipboard.
     * Supports pasting text and images.
     */
    public void handlePaste() {
        if (currentPageId == null) {
            log.warn("Cannot paste - no page loaded");
            return;
        }

        Clipboard clipboard = Clipboard.getSystemClipboard();

        // Check for image first (higher priority)
        if (clipboard.hasImage()) {
            handlePasteImage(clipboard.getImage());
        } else if (clipboard.hasString()) {
            handlePasteText(clipboard.getString());
        } else {
            log.debug("Clipboard contains no supported content (text or image)");
        }
    }

    private void handlePasteText(String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        log.info("Pasting text: {} characters", text.length());

        // Create a text element at the center of the visible area
        // Default position: center of canvas (can be adjusted)
        double x = 100;
        double y = 100;
        double width = 400;
        double height = 100;

        // Convert plain text to HTML
        String htmlContent = "<p>" + text.replace("\n", "<br>") + "</p>";

        String elementId = UUID.randomUUID().toString();
        TextElement textElement = new TextElement(
            elementId,
            x,
            y,
            width,
            height,
            htmlContent,
            0  // Default z-index
        );

        // Add to page and canvas
        pageService.addElement(currentNotebookId, currentChapterId, currentPageId, textElement);
        canvasManager.addElement(textElement);

        log.info("Created text element from clipboard paste");
    }

    private void handlePasteImage(Image image) {
        if (image == null) {
            return;
        }

        log.info("Pasting image: {}x{}", image.getWidth(), image.getHeight());

        try {
            // Convert JavaFX Image to byte array
            BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", baos);
            byte[] imageData = baos.toByteArray();

            // Generate unique filename
            String filename = "pasted-image-" + UUID.randomUUID() + ".png";

            // Save image to storage
            pageService.getImageData(currentNotebookId, currentChapterId, currentPageId, filename);
            canvasManager.getCanvasContainer().getScene().getWindow();

            // Use PageService to add the image
            double x = 100;
            double y = 100;

            ImageElement imageElement = pageService.addImage(
                currentNotebookId,
                currentChapterId,
                currentPageId,
                createTempImageFile(imageData, filename),
                x,
                y
            );

            // Add to canvas
            canvasManager.addElement(imageElement);

            log.info("Created image element from clipboard paste: {}", filename);
        } catch (IOException e) {
            log.error("Failed to paste image from clipboard", e);
        }
    }

    private java.io.File createTempImageFile(byte[] imageData, String filename) throws IOException {
        java.io.File tempFile = java.io.File.createTempFile("drawboard-paste-", ".png");
        tempFile.deleteOnExit();
        java.nio.file.Files.write(tempFile.toPath(), imageData);
        return tempFile;
    }

    /**
     * Set the background color for the canvas and text elements.
     */
    public void setBackgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor;
        canvasManager.setBackgroundColor(backgroundColor);
    }

    /**
     * Set callback for when tool settings change and toolbar needs refresh.
     */
    public void setOnToolSettingsChanged(Runnable callback) {
        this.onToolSettingsChanged = callback;
    }
}
