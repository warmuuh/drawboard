package com.drawboard.storage;

import com.drawboard.domain.Chapter;
import com.drawboard.domain.Notebook;
import com.drawboard.domain.Page;
import com.drawboard.domain.elements.TextElement;
import io.avaje.jsonb.Jsonb;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService storage;
    private Jsonb jsonb;

    @BeforeEach
    void setUp() {
        jsonb = Jsonb.builder().build();
        storage = new FileStorageService(jsonb, tempDir);
    }

    @Test
    void testSaveAndLoadNotebook() {
        // Create a notebook
        String notebookId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Notebook notebook = new Notebook(
            notebookId,
            "Test Notebook",
            now,
            now,
            new ArrayList<>()
        );

        // Save it
        storage.saveNotebook(notebook);

        // Load it back
        Notebook loaded = storage.loadNotebook(notebookId);

        assertNotNull(loaded);
        assertEquals(notebook.id(), loaded.id());
        assertEquals(notebook.name(), loaded.name());
        assertEquals(notebook.chapters().size(), loaded.chapters().size());
    }

    @Test
    void testLoadAllNotebooks() {
        // Create multiple notebooks
        Instant now = Instant.now();
        Notebook nb1 = new Notebook(
            UUID.randomUUID().toString(),
            "Notebook 1",
            now,
            now,
            new ArrayList<>()
        );
        Notebook nb2 = new Notebook(
            UUID.randomUUID().toString(),
            "Notebook 2",
            now,
            now,
            new ArrayList<>()
        );

        storage.saveNotebook(nb1);
        storage.saveNotebook(nb2);

        // Load all
        List<Notebook> notebooks = storage.loadAllNotebooks();

        assertEquals(2, notebooks.size());
    }

    @Test
    void testSaveAndLoadChapter() {
        String notebookId = UUID.randomUUID().toString();
        String chapterId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Chapter chapter = new Chapter(
            chapterId,
            "Chapter 1",
            now,
            now,
            List.of("page1", "page2")
        );

        storage.saveChapter(notebookId, chapter);
        Chapter loaded = storage.loadChapter(notebookId, chapterId);

        assertNotNull(loaded);
        assertEquals(chapter.id(), loaded.id());
        assertEquals(chapter.name(), loaded.name());
        assertEquals(2, loaded.pageIds().size());
    }

    @Test
    void testSaveAndLoadPage() {
        String notebookId = UUID.randomUUID().toString();
        String chapterId = UUID.randomUUID().toString();
        String pageId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        TextElement element = new TextElement(
            UUID.randomUUID().toString(),
            10.0,
            20.0,
            200.0,
            100.0,
            "<p>Hello World</p>",
            0
        );

        Page page = new Page(
            pageId,
            "Page 1",
            now,
            now,
            List.of(element)
        );

        storage.savePage(notebookId, chapterId, page);
        Page loaded = storage.loadPage(notebookId, chapterId, pageId);

        assertNotNull(loaded);
        assertEquals(page.id(), loaded.id());
        assertEquals(page.name(), loaded.name());
        assertEquals(1, loaded.elements().size());

        TextElement loadedElement = (TextElement) loaded.elements().get(0);
        assertEquals(element.htmlContent(), loadedElement.htmlContent());
    }

    @Test
    void testSaveAndLoadImage() {
        String notebookId = UUID.randomUUID().toString();
        String chapterId = UUID.randomUUID().toString();
        String pageId = UUID.randomUUID().toString();
        String filename = "test-image.png";

        byte[] imageData = new byte[]{1, 2, 3, 4, 5};

        storage.saveImage(notebookId, chapterId, pageId, filename, imageData);
        byte[] loaded = storage.loadImage(notebookId, chapterId, pageId, filename);

        assertNotNull(loaded);
        assertArrayEquals(imageData, loaded);
    }

    @Test
    void testDeleteNotebook() {
        String notebookId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Notebook notebook = new Notebook(
            notebookId,
            "Test Notebook",
            now,
            now,
            new ArrayList<>()
        );

        storage.saveNotebook(notebook);
        assertTrue(Files.exists(tempDir.resolve("notebook-" + notebookId)));

        storage.deleteNotebook(notebookId);
        assertFalse(Files.exists(tempDir.resolve("notebook-" + notebookId)));
    }

    @Test
    void testDeleteChapter() {
        String notebookId = UUID.randomUUID().toString();
        String chapterId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Chapter chapter = new Chapter(
            chapterId,
            "Chapter 1",
            now,
            now,
            List.of()
        );

        storage.saveChapter(notebookId, chapter);
        Path chapterDir = tempDir.resolve("notebook-" + notebookId)
            .resolve("chapter-" + chapterId);
        assertTrue(Files.exists(chapterDir));

        storage.deleteChapter(notebookId, chapterId);
        assertFalse(Files.exists(chapterDir));
    }

    @Test
    void testDeletePage() {
        String notebookId = UUID.randomUUID().toString();
        String chapterId = UUID.randomUUID().toString();
        String pageId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Page page = new Page(
            pageId,
            "Page 1",
            now,
            now,
            List.of()
        );

        storage.savePage(notebookId, chapterId, page);
        Path pageDir = tempDir.resolve("notebook-" + notebookId)
            .resolve("chapter-" + chapterId)
            .resolve("page-" + pageId);
        assertTrue(Files.exists(pageDir));

        storage.deletePage(notebookId, chapterId, pageId);
        assertFalse(Files.exists(pageDir));
    }
}
