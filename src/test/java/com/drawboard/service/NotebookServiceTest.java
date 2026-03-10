package com.drawboard.service;

import com.drawboard.domain.Chapter;
import com.drawboard.domain.Notebook;
import com.drawboard.storage.FileStorageService;
import io.avaje.jsonb.Jsonb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotebookServiceTest {

    @TempDir
    Path tempDir;

    private NotebookService notebookService;
    private FileStorageService storage;

    @BeforeEach
    void setUp() {
        Jsonb jsonb = Jsonb.builder().build();
        storage = new FileStorageService(jsonb, tempDir);
        notebookService = new NotebookService(storage);
    }

    @Test
    void testCreateNotebook() {
        Notebook notebook = notebookService.createNotebook("My Notebook");

        assertNotNull(notebook);
        assertNotNull(notebook.id());
        assertEquals("My Notebook", notebook.name());
        assertNotNull(notebook.created());
        assertNotNull(notebook.modified());
        assertEquals(0, notebook.chapters().size());
    }

    @Test
    void testGetAllNotebooks() {
        notebookService.createNotebook("Notebook 1");
        notebookService.createNotebook("Notebook 2");

        List<Notebook> notebooks = notebookService.getAllNotebooks();

        assertEquals(2, notebooks.size());
    }

    @Test
    void testRenameNotebook() {
        Notebook notebook = notebookService.createNotebook("Old Name");

        Notebook renamed = notebookService.renameNotebook(notebook.id(), "New Name");

        assertNotNull(renamed);
        assertEquals("New Name", renamed.name());
        assertEquals(notebook.id(), renamed.id());
    }

    @Test
    void testDeleteNotebook() {
        Notebook notebook = notebookService.createNotebook("To Delete");
        String notebookId = notebook.id();

        notebookService.deleteNotebook(notebookId);

        assertNull(notebookService.getNotebook(notebookId));
    }

    @Test
    void testCreateChapter() {
        Notebook notebook = notebookService.createNotebook("Test Notebook");

        Chapter chapter = notebookService.createChapter(notebook.id(), "Chapter 1");

        assertNotNull(chapter);
        assertNotNull(chapter.id());
        assertEquals("Chapter 1", chapter.name());
        assertEquals(0, chapter.pageIds().size());

        // Verify notebook was updated
        Notebook updated = notebookService.getNotebook(notebook.id());
        assertEquals(1, updated.chapters().size());
        assertEquals(chapter.id(), updated.chapters().get(0).id());
    }

    @Test
    void testRenameChapter() {
        Notebook notebook = notebookService.createNotebook("Test Notebook");
        Chapter chapter = notebookService.createChapter(notebook.id(), "Old Chapter Name");

        Chapter renamed = notebookService.renameChapter(notebook.id(), chapter.id(), "New Chapter Name");

        assertNotNull(renamed);
        assertEquals("New Chapter Name", renamed.name());
        assertEquals(chapter.id(), renamed.id());
    }

    @Test
    void testDeleteChapter() {
        Notebook notebook = notebookService.createNotebook("Test Notebook");
        Chapter chapter = notebookService.createChapter(notebook.id(), "To Delete");

        notebookService.deleteChapter(notebook.id(), chapter.id());

        assertNull(notebookService.getChapter(notebook.id(), chapter.id()));

        // Verify notebook was updated
        Notebook updated = notebookService.getNotebook(notebook.id());
        assertEquals(0, updated.chapters().size());
    }

    @Test
    void testCreateMultipleChapters() {
        Notebook notebook = notebookService.createNotebook("Test Notebook");

        Chapter ch1 = notebookService.createChapter(notebook.id(), "Chapter 1");
        Chapter ch2 = notebookService.createChapter(notebook.id(), "Chapter 2");
        Chapter ch3 = notebookService.createChapter(notebook.id(), "Chapter 3");

        Notebook updated = notebookService.getNotebook(notebook.id());
        assertEquals(3, updated.chapters().size());
    }

    @Test
    void testRenameNotebookNotFound() {
        assertThrows(IllegalArgumentException.class, () -> {
            notebookService.renameNotebook("non-existent-id", "New Name");
        });
    }

    @Test
    void testCreateChapterNotebookNotFound() {
        assertThrows(IllegalArgumentException.class, () -> {
            notebookService.createChapter("non-existent-id", "Chapter");
        });
    }
}
