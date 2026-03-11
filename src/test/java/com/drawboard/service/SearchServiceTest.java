package com.drawboard.service;

import com.drawboard.domain.Chapter;
import com.drawboard.domain.Notebook;
import com.drawboard.domain.Page;
import com.drawboard.domain.elements.CanvasElement;
import com.drawboard.domain.elements.TextElement;
import com.drawboard.domain.search.SearchMatchType;
import com.drawboard.domain.search.SearchOptions;
import com.drawboard.domain.search.SearchResult;
import com.drawboard.storage.FileStorageService;
import io.avaje.jsonb.Jsonb;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService storage;
    private NotebookService notebookService;
    private PageService pageService;
    private SearchService searchService;

    private Notebook testNotebook;
    private Chapter testChapter;
    private Page testPage;

    @BeforeEach
    void setUp() {
        Jsonb jsonb = Jsonb.builder().build();
        storage = new FileStorageService(jsonb, tempDir);
        notebookService = new NotebookService(storage);
        pageService = new PageService(storage, notebookService);
        searchService = new SearchService(notebookService, pageService);

        // Create test data
        testNotebook = notebookService.createNotebook("Test Notebook");
        testChapter = notebookService.createChapter(testNotebook.id(), "Test Chapter");
        testPage = pageService.createPage(testNotebook.id(), testChapter.id(), "Test Page");
    }

    @AfterEach
    void tearDown() {
        if (testNotebook != null) {
            notebookService.deleteNotebook(testNotebook.id());
        }
    }

    @Test
    void testSearchInPageName() {
        List<SearchResult> results = searchService.searchNotebook(
            testNotebook.id(),
            "Test Page",
            SearchOptions.defaults()
        );

        assertEquals(1, results.size());
        SearchResult result = results.get(0);
        assertEquals(testPage.id(), result.pageId());
        assertEquals(SearchMatchType.PAGE_NAME, result.match().matchType());
        assertTrue(result.match().snippet().contains("Test Page"));
    }

    @Test
    void testSearchInChapterName() {
        List<SearchResult> results = searchService.searchNotebook(
            testNotebook.id(),
            "Test Chapter",
            SearchOptions.defaults()
        );

        assertEquals(1, results.size());
        SearchResult result = results.get(0);
        assertEquals(testChapter.id(), result.chapterId());
        assertEquals(SearchMatchType.CHAPTER_NAME, result.match().matchType());
        assertTrue(result.match().snippet().contains("Test Chapter"));
    }

    @Test
    void testSearchInTextElement() {
        // Add a text element with some content
        TextElement textElement = new TextElement(
            "elem-1",
            100,
            100,
            200,
            100,
            "<p>Hello <b>world</b>, this is a test!</p>",
            0
        );
        pageService.addElement(testNotebook.id(), testChapter.id(), testPage.id(), textElement);

        List<SearchResult> results = searchService.searchNotebook(
            testNotebook.id(),
            "world",
            SearchOptions.defaults()
        );

        // Should find in text element (HTML stripped)
        boolean foundInText = results.stream()
            .anyMatch(r -> r.match().matchType() == SearchMatchType.TEXT_ELEMENT);
        assertTrue(foundInText);
    }

    @Test
    void testSearchCaseInsensitive() {
        TextElement textElement = new TextElement(
            "elem-1",
            100,
            100,
            200,
            100,
            "<p>Java Programming</p>",
            0
        );
        pageService.addElement(testNotebook.id(), testChapter.id(), testPage.id(), textElement);

        // Search with lowercase
        List<SearchResult> results = searchService.searchNotebook(
            testNotebook.id(),
            "java",
            SearchOptions.defaults()
        );

        assertTrue(results.stream()
            .anyMatch(r -> r.match().matchType() == SearchMatchType.TEXT_ELEMENT));
    }

    @Test
    void testSearchCaseSensitive() {
        TextElement textElement = new TextElement(
            "elem-1",
            100,
            100,
            200,
            100,
            "<p>Java Programming</p>",
            0
        );
        pageService.addElement(testNotebook.id(), testChapter.id(), testPage.id(), textElement);

        // Search with case sensitivity enabled
        SearchOptions options = SearchOptions.builder()
            .caseSensitive(true)
            .build();

        List<SearchResult> results = searchService.searchNotebook(
            testNotebook.id(),
            "java",
            options
        );

        // Should not find "Java" when searching for "java" case-sensitively
        assertFalse(results.stream()
            .anyMatch(r -> r.match().matchType() == SearchMatchType.TEXT_ELEMENT));
    }

    @Test
    void testSearchWholeWord() {
        TextElement textElement = new TextElement(
            "elem-1",
            100,
            100,
            200,
            100,
            "<p>The cat and the catalog are here</p>",
            0
        );
        pageService.addElement(testNotebook.id(), testChapter.id(), testPage.id(), textElement);

        SearchOptions options = SearchOptions.builder()
            .wholeWord(true)
            .build();

        List<SearchResult> results = searchService.searchNotebook(
            testNotebook.id(),
            "cat",
            options
        );

        // Should find "cat" but not "catalog"
        long textMatches = results.stream()
            .filter(r -> r.match().matchType() == SearchMatchType.TEXT_ELEMENT)
            .count();

        // Should only match "cat" as a whole word (once), not "cat" in "catalog"
        assertEquals(1, textMatches);
    }

    @Test
    void testSearchMaxResults() {
        // Create multiple pages with matching content
        for (int i = 0; i < 5; i++) {
            Page page = pageService.createPage(testNotebook.id(), testChapter.id(), "Page " + i);
            TextElement element = new TextElement(
                "elem-" + i,
                100,
                100,
                200,
                100,
                "<p>Find me!</p>",
                0
            );
            pageService.addElement(testNotebook.id(), testChapter.id(), page.id(), element);
        }

        SearchOptions options = SearchOptions.builder()
            .maxResults(3)
            .build();

        List<SearchResult> results = searchService.searchNotebook(
            testNotebook.id(),
            "Find me",
            options
        );

        // Should be limited to 3 results
        assertTrue(results.size() <= 3);
    }

    @Test
    void testSearchAllNotebooks() {
        // Create another notebook
        Notebook notebook2 = notebookService.createNotebook("Second Notebook");
        Chapter chapter2 = notebookService.createChapter(notebook2.id(), "Chapter 2");
        Page page2 = pageService.createPage(notebook2.id(), chapter2.id(), "Another Page");

        TextElement element1 = new TextElement(
            "elem-1",
            100,
            100,
            200,
            100,
            "<p>Searchable content</p>",
            0
        );
        pageService.addElement(testNotebook.id(), testChapter.id(), testPage.id(), element1);

        TextElement element2 = new TextElement(
            "elem-2",
            100,
            100,
            200,
            100,
            "<p>Searchable content</p>",
            0
        );
        pageService.addElement(notebook2.id(), chapter2.id(), page2.id(), element2);

        List<SearchResult> results = searchService.searchAllNotebooks(
            "Searchable",
            SearchOptions.defaults()
        );

        // Should find results in both notebooks
        assertTrue(results.size() >= 2);
        assertTrue(results.stream().anyMatch(r -> r.notebookId().equals(testNotebook.id())));
        assertTrue(results.stream().anyMatch(r -> r.notebookId().equals(notebook2.id())));

        // Cleanup
        notebookService.deleteNotebook(notebook2.id());
    }

    @Test
    void testSearchEmptyQuery() {
        List<SearchResult> results = searchService.searchNotebook(
            testNotebook.id(),
            "",
            SearchOptions.defaults()
        );

        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchNonExistentContent() {
        List<SearchResult> results = searchService.searchNotebook(
            testNotebook.id(),
            "NonExistentContent123",
            SearchOptions.defaults()
        );

        assertTrue(results.isEmpty());
    }
}
