package com.drawboard.service;

import com.drawboard.domain.Chapter;
import com.drawboard.domain.Notebook;
import com.drawboard.domain.Page;
import com.drawboard.domain.elements.TextElement;
import com.drawboard.storage.FileStorageService;
import io.avaje.jsonb.Jsonb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PageServiceTest {

    @TempDir
    Path tempDir;

    private PageService pageService;
    private NotebookService notebookService;
    private FileStorageService storage;

    private String notebookId;
    private String chapterId;

    @BeforeEach
    void setUp() {
        Jsonb jsonb = Jsonb.builder().build();
        storage = new FileStorageService(jsonb, tempDir);
        notebookService = new NotebookService(storage);
        pageService = new PageService(storage, notebookService);

        // Create a notebook and chapter for testing
        Notebook notebook = notebookService.createNotebook("Test Notebook");
        notebookId = notebook.id();

        Chapter chapter = notebookService.createChapter(notebookId, "Test Chapter");
        chapterId = chapter.id();
    }

    @Test
    void testCreatePage() {
        Page page = pageService.createPage(notebookId, chapterId);

        assertNotNull(page);
        assertNotNull(page.id());
        assertEquals("Untitled Page", page.name());
        assertEquals(0, page.elements().size());

        // Verify chapter was updated
        Chapter updated = notebookService.getChapter(notebookId, chapterId);
        assertEquals(1, updated.pageIds().size());
        assertEquals(page.id(), updated.pageIds().get(0));
    }

    @Test
    void testCreatePageWithName() {
        Page page = pageService.createPage(notebookId, chapterId, "My Page");

        assertNotNull(page);
        assertEquals("My Page", page.name());
    }

    @Test
    void testRenamePage() {
        Page page = pageService.createPage(notebookId, chapterId, "Old Name");

        Page renamed = pageService.renamePage(notebookId, chapterId, page.id(), "New Name");

        assertNotNull(renamed);
        assertEquals("New Name", renamed.name());
        assertEquals(page.id(), renamed.id());
    }

    @Test
    void testDeletePage() {
        Page page = pageService.createPage(notebookId, chapterId);
        String pageId = page.id();

        pageService.deletePage(notebookId, chapterId, pageId);

        assertNull(pageService.getPage(notebookId, chapterId, pageId));

        // Verify chapter was updated
        Chapter updated = notebookService.getChapter(notebookId, chapterId);
        assertEquals(0, updated.pageIds().size());
    }

    @Test
    void testAddElement() {
        Page page = pageService.createPage(notebookId, chapterId);

        TextElement element = new TextElement(
            UUID.randomUUID().toString(),
            10.0,
            20.0,
            200.0,
            100.0,
            "<p>Test</p>",
            0
        );

        pageService.addElement(notebookId, chapterId, page.id(), element);

        // Verify element was added
        Page updated = pageService.getPage(notebookId, chapterId, page.id());
        assertEquals(1, updated.elements().size());
        assertEquals(element.id(), updated.elements().get(0).id());
    }

    @Test
    void testUpdateElement() {
        Page page = pageService.createPage(notebookId, chapterId);

        TextElement element = new TextElement(
            UUID.randomUUID().toString(),
            10.0,
            20.0,
            200.0,
            100.0,
            "<p>Original</p>",
            0
        );

        pageService.addElement(notebookId, chapterId, page.id(), element);

        // Update the element
        TextElement updated = new TextElement(
            element.id(),
            10.0,
            20.0,
            200.0,
            100.0,
            "<p>Updated</p>",
            0
        );

        pageService.updateElement(notebookId, chapterId, page.id(), updated);

        // Verify update
        Page reloaded = pageService.getPage(notebookId, chapterId, page.id());
        TextElement reloadedElement = (TextElement) reloaded.elements().get(0);
        assertEquals("<p>Updated</p>", reloadedElement.htmlContent());
    }

    @Test
    void testDeleteElement() {
        Page page = pageService.createPage(notebookId, chapterId);

        TextElement element1 = new TextElement(
            UUID.randomUUID().toString(),
            10.0,
            20.0,
            200.0,
            100.0,
            "<p>Element 1</p>",
            0
        );

        TextElement element2 = new TextElement(
            UUID.randomUUID().toString(),
            30.0,
            40.0,
            200.0,
            100.0,
            "<p>Element 2</p>",
            1
        );

        pageService.addElement(notebookId, chapterId, page.id(), element1);
        pageService.addElement(notebookId, chapterId, page.id(), element2);

        // Delete first element
        pageService.deleteElement(notebookId, chapterId, page.id(), element1.id());

        // Verify deletion
        Page updated = pageService.getPage(notebookId, chapterId, page.id());
        assertEquals(1, updated.elements().size());
        assertEquals(element2.id(), updated.elements().get(0).id());
    }

    @Test
    void testCreatePageChapterNotFound() {
        assertThrows(IllegalArgumentException.class, () -> {
            pageService.createPage(notebookId, "non-existent-chapter");
        });
    }

    @Test
    void testAddElementPageNotFound() {
        TextElement element = new TextElement(
            UUID.randomUUID().toString(),
            10.0,
            20.0,
            200.0,
            100.0,
            "<p>Test</p>",
            0
        );

        assertThrows(IllegalArgumentException.class, () -> {
            pageService.addElement(notebookId, chapterId, "non-existent-page", element);
        });
    }
}
