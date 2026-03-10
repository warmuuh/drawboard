package com.drawboard.ui;

import com.drawboard.canvas.CanvasManager;
import com.drawboard.domain.Page;
import com.drawboard.service.PageService;
import com.drawboard.service.PreferencesService;
import javafx.scene.layout.Pane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public CanvasEditorController(PageService pageService, PreferencesService preferencesService, Pane canvasContainer) {
        this.pageService = pageService;
        this.preferencesService = preferencesService;
        this.canvasManager = new CanvasManager(canvasContainer);

        // Set up listeners for new elements
        setupElementListeners();
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
}
