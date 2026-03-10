package com.drawboard.service;

import com.drawboard.domain.Chapter;
import com.drawboard.domain.Notebook;
import com.drawboard.storage.FileStorageService;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing notebooks and chapters.
 * Provides high-level operations for the notebook hierarchy.
 */
@Singleton
public class NotebookService {
    private static final Logger log = LoggerFactory.getLogger(NotebookService.class);

    private final FileStorageService storage;

    public NotebookService(FileStorageService storage) {
        this.storage = storage;
    }

    // ==================== Notebook Operations ====================

    public List<Notebook> getAllNotebooks() {
        return storage.loadAllNotebooks();
    }

    public Notebook getNotebook(String notebookId) {
        return storage.loadNotebook(notebookId);
    }

    public Notebook createNotebook(String name) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Notebook notebook = new Notebook(
            id,
            name,
            now,
            now,
            new ArrayList<>()
        );

        storage.saveNotebook(notebook);
        log.info("Created notebook: {} ({})", name, id);

        return notebook;
    }

    public Notebook renameNotebook(String notebookId, String newName) {
        Notebook notebook = storage.loadNotebook(notebookId);
        if (notebook == null) {
            log.warn("Cannot rename notebook - not found: {}", notebookId);
            throw new IllegalArgumentException("Notebook not found: " + notebookId);
        }

        Notebook updated = new Notebook(
            notebook.id(),
            newName,
            notebook.created(),
            Instant.now(),
            notebook.chapters()
        );

        storage.saveNotebook(updated);
        log.info("Renamed notebook {} to: {}", notebookId, newName);

        return updated;
    }

    public void deleteNotebook(String notebookId) {
        storage.deleteNotebook(notebookId);
        log.info("Deleted notebook: {}", notebookId);
    }

    // ==================== Chapter Operations ====================

    public Chapter getChapter(String notebookId, String chapterId) {
        return storage.loadChapter(notebookId, chapterId);
    }

    public Chapter createChapter(String notebookId, String name) {
        Notebook notebook = storage.loadNotebook(notebookId);
        if (notebook == null) {
            log.warn("Cannot create chapter - notebook not found: {}", notebookId);
            throw new IllegalArgumentException("Notebook not found: " + notebookId);
        }

        String chapterId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Chapter chapter = new Chapter(
            chapterId,
            name,
            now,
            now,
            new ArrayList<>()
        );

        // Save the chapter
        storage.saveChapter(notebookId, chapter);

        // Update notebook to include the chapter
        List<Chapter> updatedChapters = new ArrayList<>(notebook.chapters());
        updatedChapters.add(chapter);

        Notebook updatedNotebook = new Notebook(
            notebook.id(),
            notebook.name(),
            notebook.created(),
            now,
            updatedChapters
        );

        storage.saveNotebook(updatedNotebook);
        log.info("Created chapter: {} in notebook {}", name, notebookId);

        return chapter;
    }

    /**
     * Update a chapter within the notebook's chapter list.
     * Used when a chapter is modified (e.g., pages added) to keep notebook.json in sync.
     */
    public void updateChapterInNotebook(String notebookId, Chapter updatedChapter) {
        Notebook notebook = storage.loadNotebook(notebookId);
        if (notebook == null) {
            log.warn("Cannot update chapter in notebook - notebook not found: {}", notebookId);
            return;
        }

        List<Chapter> updatedChapters = notebook.chapters().stream()
            .map(ch -> ch.id().equals(updatedChapter.id()) ? updatedChapter : ch)
            .toList();

        Notebook updatedNotebook = new Notebook(
            notebook.id(),
            notebook.name(),
            notebook.created(),
            Instant.now(),
            updatedChapters
        );

        storage.saveNotebook(updatedNotebook);
        log.debug("Updated chapter {} in notebook {}", updatedChapter.id(), notebookId);
    }

    public Chapter renameChapter(String notebookId, String chapterId, String newName) {
        Chapter chapter = storage.loadChapter(notebookId, chapterId);
        if (chapter == null) {
            log.warn("Cannot rename chapter - not found: {}", chapterId);
            throw new IllegalArgumentException("Chapter not found: " + chapterId);
        }

        Chapter updated = new Chapter(
            chapter.id(),
            newName,
            chapter.created(),
            Instant.now(),
            chapter.pageIds()
        );

        storage.saveChapter(notebookId, updated);

        // Update notebook metadata
        updateNotebookModifiedTime(notebookId);

        log.info("Renamed chapter {} to: {}", chapterId, newName);

        return updated;
    }

    public void deleteChapter(String notebookId, String chapterId) {
        Notebook notebook = storage.loadNotebook(notebookId);
        if (notebook == null) {
            log.warn("Cannot delete chapter - notebook not found: {}", notebookId);
            throw new IllegalArgumentException("Notebook not found: " + notebookId);
        }

        // Delete the chapter directory
        storage.deleteChapter(notebookId, chapterId);

        // Update notebook to remove the chapter
        List<Chapter> updatedChapters = notebook.chapters().stream()
            .filter(ch -> !ch.id().equals(chapterId))
            .toList();

        Notebook updatedNotebook = new Notebook(
            notebook.id(),
            notebook.name(),
            notebook.created(),
            Instant.now(),
            updatedChapters
        );

        storage.saveNotebook(updatedNotebook);
        log.info("Deleted chapter: {} from notebook {}", chapterId, notebookId);
    }

    // ==================== Helper Methods ====================

    private void updateNotebookModifiedTime(String notebookId) {
        Notebook notebook = storage.loadNotebook(notebookId);
        if (notebook != null) {
            Notebook updated = new Notebook(
                notebook.id(),
                notebook.name(),
                notebook.created(),
                Instant.now(),
                notebook.chapters()
            );
            storage.saveNotebook(updated);
        }
    }
}
