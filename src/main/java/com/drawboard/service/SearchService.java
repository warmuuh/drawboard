package com.drawboard.service;

import com.drawboard.domain.Chapter;
import com.drawboard.domain.Notebook;
import com.drawboard.domain.Page;
import com.drawboard.domain.elements.CanvasElement;
import com.drawboard.domain.elements.TextElement;
import com.drawboard.domain.search.SearchMatch;
import com.drawboard.domain.search.SearchMatchType;
import com.drawboard.domain.search.SearchOptions;
import com.drawboard.domain.search.SearchResult;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for searching content across notebooks and pages.
 * Supports case-sensitive/insensitive search, whole word matching, and regex.
 */
@Singleton
public class SearchService {
    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final NotebookService notebookService;
    private final PageService pageService;

    public SearchService(NotebookService notebookService, PageService pageService) {
        this.notebookService = notebookService;
        this.pageService = pageService;
    }

    /**
     * Search all notebooks for the given query.
     */
    public List<SearchResult> searchAllNotebooks(String query, SearchOptions options) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<SearchResult> allResults = new ArrayList<>();
        List<Notebook> notebooks = notebookService.getAllNotebooks();

        for (Notebook notebook : notebooks) {
            List<SearchResult> notebookResults = searchNotebook(notebook.id(), query, options);
            allResults.addAll(notebookResults);

            if (allResults.size() >= options.maxResults()) {
                break;
            }
        }

        return allResults.stream()
            .limit(options.maxResults())
            .toList();
    }

    /**
     * Search within a specific notebook for the given query.
     */
    public List<SearchResult> searchNotebook(String notebookId, String query, SearchOptions options) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        Notebook notebook = notebookService.getNotebook(notebookId);
        if (notebook == null) {
            log.warn("Cannot search - notebook not found: {}", notebookId);
            return List.of();
        }

        List<SearchResult> results = new ArrayList<>();
        Pattern searchPattern = createSearchPattern(query, options);

        for (Chapter chapter : notebook.chapters()) {
            // Search chapter name
            results.addAll(searchInText(
                chapter.name(),
                searchPattern,
                options,
                notebook,
                chapter,
                null,
                SearchMatchType.CHAPTER_NAME
            ));

            // Search pages in chapter
            for (String pageId : chapter.pageIds()) {
                Page page = pageService.getPage(notebookId, chapter.id(), pageId);
                if (page != null) {
                    results.addAll(searchPage(notebook, chapter, page, searchPattern, options));
                }

                if (results.size() >= options.maxResults()) {
                    break;
                }
            }

            if (results.size() >= options.maxResults()) {
                break;
            }
        }

        return results.stream()
            .limit(options.maxResults())
            .toList();
    }

    /**
     * Search within a single page.
     */
    private List<SearchResult> searchPage(Notebook notebook, Chapter chapter, Page page,
                                         Pattern searchPattern, SearchOptions options) {
        List<SearchResult> results = new ArrayList<>();

        // Search page name
        results.addAll(searchInText(
            page.name(),
            searchPattern,
            options,
            notebook,
            chapter,
            page,
            SearchMatchType.PAGE_NAME
        ));

        // Search text elements
        for (CanvasElement element : page.elements()) {
            if (element instanceof TextElement textElement) {
                String plainText = TextExtractor.extractPlainText(textElement.htmlContent());
                results.addAll(searchInText(
                    plainText,
                    searchPattern,
                    options,
                    notebook,
                    chapter,
                    page,
                    SearchMatchType.TEXT_ELEMENT,
                    textElement.id()
                ));
            }
        }

        return results;
    }

    /**
     * Search for matches in a text string.
     */
    private List<SearchResult> searchInText(String text, Pattern searchPattern, SearchOptions options,
                                           Notebook notebook, Chapter chapter, Page page,
                                           SearchMatchType matchType) {
        return searchInText(text, searchPattern, options, notebook, chapter, page, matchType, null);
    }

    /**
     * Search for matches in a text string with optional element ID.
     */
    private List<SearchResult> searchInText(String text, Pattern searchPattern, SearchOptions options,
                                           Notebook notebook, Chapter chapter, Page page,
                                           SearchMatchType matchType, String elementId) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<SearchResult> results = new ArrayList<>();
        Matcher matcher = searchPattern.matcher(text);

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            String snippet = TextExtractor.createSnippet(
                text,
                start,
                end,
                options.snippetContextLength()
            );

            SearchMatch match = new SearchMatch(
                elementId,
                matchType,
                snippet,
                start,
                end
            );

            SearchResult result = new SearchResult(
                notebook.id(),
                notebook.name(),
                chapter.id(),
                chapter.name(),
                page != null ? page.id() : null,
                page != null ? page.name() : null,
                match
            );

            results.add(result);
        }

        return results;
    }

    /**
     * Create a regex pattern based on the query and options.
     */
    private Pattern createSearchPattern(String query, SearchOptions options) {
        String patternString;

        if (options.useRegex()) {
            patternString = query;
        } else {
            // Escape regex special characters
            patternString = Pattern.quote(query);
        }

        // Add word boundary markers if whole word matching
        if (options.wholeWord()) {
            patternString = "\\b" + patternString + "\\b";
        }

        int flags = options.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE;

        try {
            return Pattern.compile(patternString, flags);
        } catch (Exception e) {
            log.warn("Invalid search pattern: {}", query, e);
            // Fall back to literal match
            return Pattern.compile(Pattern.quote(query), flags);
        }
    }
}
