package com.drawboard.service;

import com.drawboard.domain.Chapter;
import com.drawboard.domain.Page;
import com.drawboard.domain.elements.CanvasElement;
import com.drawboard.domain.elements.ImageElement;
import com.drawboard.storage.FileStorageService;
import com.drawboard.webrtc.PageUpdateEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Service for managing pages and canvas elements.
 * Provides high-level operations for page content.
 */
@Singleton
public class PageService {
    private static final Logger log = LoggerFactory.getLogger(PageService.class);

    private final FileStorageService storage;
    private final NotebookService notebookService;
    private final List<Consumer<PageUpdateEvent>> updateListeners = new CopyOnWriteArrayList<>();

    public PageService(FileStorageService storage, NotebookService notebookService) {
        this.storage = storage;
        this.notebookService = notebookService;
    }

    /**
     * Add a listener that will be notified when pages are updated.
     *
     * @param listener The listener to add
     */
    public void addUpdateListener(Consumer<PageUpdateEvent> listener) {
        updateListeners.add(listener);
    }

    /**
     * Remove a previously registered update listener.
     *
     * @param listener The listener to remove
     */
    public void removeUpdateListener(Consumer<PageUpdateEvent> listener) {
        updateListeners.remove(listener);
    }

    /**
     * Notify all update listeners of a page change.
     */
    private void notifyUpdateListeners(String notebookId, String chapterId, String pageId, Page page) {
        PageUpdateEvent event = new PageUpdateEvent(notebookId, chapterId, pageId, page);
        for (Consumer<PageUpdateEvent> listener : updateListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("Error notifying update listener", e);
            }
        }
    }

    // ==================== Page Operations ====================

    public Page getPage(String notebookId, String chapterId, String pageId) {
        return storage.loadPage(notebookId, chapterId, pageId);
    }

    public Page createPage(String notebookId, String chapterId) {
        return createPage(notebookId, chapterId, "Untitled Page");
    }

    public Page createPage(String notebookId, String chapterId, String name) {
        Chapter chapter = notebookService.getChapter(notebookId, chapterId);
        if (chapter == null) {
            log.warn("Cannot create page - chapter not found: {}", chapterId);
            throw new IllegalArgumentException("Chapter not found: " + chapterId);
        }

        String pageId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Page page = new Page(
            pageId,
            name,
            now,
            now,
            new ArrayList<>()
        );

        // Save the page
        storage.savePage(notebookId, chapterId, page);

        // Update chapter to include the page ID
        List<String> updatedPageIds = new ArrayList<>(chapter.pageIds());
        updatedPageIds.add(pageId);

        Chapter updatedChapter = new Chapter(
            chapter.id(),
            chapter.name(),
            chapter.created(),
            now,
            updatedPageIds
        );

        storage.saveChapter(notebookId, updatedChapter);

        // Also update the notebook to reflect the updated chapter
        notebookService.updateChapterInNotebook(notebookId, updatedChapter);

        log.info("Created page: {} in chapter {}", name, chapterId);

        return page;
    }

    public Page renamePage(String notebookId, String chapterId, String pageId, String newName) {
        Page page = storage.loadPage(notebookId, chapterId, pageId);
        if (page == null) {
            log.warn("Cannot rename page - not found: {}", pageId);
            throw new IllegalArgumentException("Page not found: " + pageId);
        }

        Page updated = new Page(
            page.id(),
            newName,
            page.created(),
            Instant.now(),
            page.elements()
        );

        storage.savePage(notebookId, chapterId, updated);
        log.info("Renamed page {} to: {}", pageId, newName);

        return updated;
    }

    public void savePage(String notebookId, String chapterId, Page page) {
        storage.savePage(notebookId, chapterId, page);
        notifyUpdateListeners(notebookId, chapterId, page.id(), page);
    }

    public void deletePage(String notebookId, String chapterId, String pageId) {
        Chapter chapter = notebookService.getChapter(notebookId, chapterId);
        if (chapter == null) {
            log.warn("Cannot delete page - chapter not found: {}", chapterId);
            throw new IllegalArgumentException("Chapter not found: " + chapterId);
        }

        // Delete the page directory
        storage.deletePage(notebookId, chapterId, pageId);

        // Update chapter to remove the page ID
        List<String> updatedPageIds = chapter.pageIds().stream()
            .filter(id -> !id.equals(pageId))
            .toList();

        Chapter updatedChapter = new Chapter(
            chapter.id(),
            chapter.name(),
            chapter.created(),
            Instant.now(),
            updatedPageIds
        );

        storage.saveChapter(notebookId, updatedChapter);

        // Also update the notebook to reflect the updated chapter
        notebookService.updateChapterInNotebook(notebookId, updatedChapter);

        log.info("Deleted page: {} from chapter {}", pageId, chapterId);
    }

    // ==================== Element Operations ====================

    public void addElement(String notebookId, String chapterId, String pageId,
                          CanvasElement element) {
        Page page = storage.loadPage(notebookId, chapterId, pageId);
        if (page == null) {
            log.warn("Cannot add element - page not found: {}", pageId);
            throw new IllegalArgumentException("Page not found: " + pageId);
        }

        List<CanvasElement> updatedElements = new ArrayList<>(page.elements());
        updatedElements.add(element);

        Page updatedPage = new Page(
            page.id(),
            page.name(),
            page.created(),
            Instant.now(),
            updatedElements
        );

        storage.savePage(notebookId, chapterId, updatedPage);
        notifyUpdateListeners(notebookId, chapterId, pageId, updatedPage);
        log.debug("Added element {} to page {}", element.id(), pageId);
    }

    public void updateElement(String notebookId, String chapterId, String pageId,
                             CanvasElement element) {
        Page page = storage.loadPage(notebookId, chapterId, pageId);
        if (page == null) {
            log.warn("Cannot update element - page not found: {}", pageId);
            throw new IllegalArgumentException("Page not found: " + pageId);
        }

        List<CanvasElement> updatedElements = page.elements().stream()
            .map(e -> e.id().equals(element.id()) ? element : e)
            .toList();

        Page updatedPage = new Page(
            page.id(),
            page.name(),
            page.created(),
            Instant.now(),
            updatedElements
        );

        storage.savePage(notebookId, chapterId, updatedPage);
        notifyUpdateListeners(notebookId, chapterId, pageId, updatedPage);
        log.debug("Updated element {} on page {}", element.id(), pageId);
    }

    public void deleteElement(String notebookId, String chapterId, String pageId,
                             String elementId) {
        Page page = storage.loadPage(notebookId, chapterId, pageId);
        if (page == null) {
            log.warn("Cannot delete element - page not found: {}", pageId);
            throw new IllegalArgumentException("Page not found: " + pageId);
        }

        // Find the element to check if it's an image (need to delete the file)
        CanvasElement elementToDelete = page.elements().stream()
            .filter(e -> e.id().equals(elementId))
            .findFirst()
            .orElse(null);

        // If it's an image element, delete the image file
        if (elementToDelete instanceof ImageElement imageElement) {
            storage.deleteImage(notebookId, chapterId, pageId, imageElement.filename());
            log.info("Deleted image file: {}", imageElement.filename());
        }

        List<CanvasElement> updatedElements = page.elements().stream()
            .filter(e -> !e.id().equals(elementId))
            .toList();

        Page updatedPage = new Page(
            page.id(),
            page.name(),
            page.created(),
            Instant.now(),
            updatedElements
        );

        storage.savePage(notebookId, chapterId, updatedPage);
        notifyUpdateListeners(notebookId, chapterId, pageId, updatedPage);
        log.debug("Deleted element {} from page {}", elementId, pageId);
    }

    // ==================== Image Handling ====================

    public ImageElement addImage(String notebookId, String chapterId, String pageId,
                                File imageFile, double x, double y) throws IOException {
        if (!imageFile.exists()) {
            throw new IllegalArgumentException("Image file does not exist: " + imageFile);
        }

        // Generate unique filename
        String extension = getFileExtension(imageFile.getName());
        String filename = "image-" + UUID.randomUUID() + extension;

        // Read image data
        byte[] imageData = Files.readAllBytes(imageFile.toPath());

        // Save image to storage
        storage.saveImage(notebookId, chapterId, pageId, filename, imageData);

        // Create image element
        String elementId = UUID.randomUUID().toString();
        ImageElement imageElement = new ImageElement(
            elementId,
            x,
            y,
            0.0,  // Width will be set by UI when image is loaded
            0.0,  // Height will be set by UI when image is loaded
            filename,
            0     // Default z-index
        );

        // Add element to page
        addElement(notebookId, chapterId, pageId, imageElement);

        log.info("Added image {} to page {}", filename, pageId);

        return imageElement;
    }

    public byte[] getImageData(String notebookId, String chapterId, String pageId,
                              String filename) {
        return storage.loadImage(notebookId, chapterId, pageId, filename);
    }

    // ==================== Helper Methods ====================

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot);
        }
        return "";
    }
}
