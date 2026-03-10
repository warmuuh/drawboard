package com.drawboard.util;

import com.drawboard.domain.Page;
import com.drawboard.domain.elements.*;
import com.drawboard.service.NotebookService;
import com.drawboard.service.PageService;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Utility to generate sample data for testing and demonstration.
 */
@Singleton
public class SampleDataGenerator {
    private static final Logger log = LoggerFactory.getLogger(SampleDataGenerator.class);

    private final NotebookService notebookService;
    private final PageService pageService;

    public SampleDataGenerator(NotebookService notebookService, PageService pageService) {
        this.notebookService = notebookService;
        this.pageService = pageService;
    }

    /**
     * Create a sample notebook with various element types for demonstration.
     */
    public void createSampleNotebook() {
        // Check if sample already exists
        var notebooks = notebookService.getAllNotebooks();
        boolean hasSample = notebooks.stream()
            .anyMatch(nb -> nb.name().equals("Sample Notebook"));

        if (hasSample) {
            log.debug("Sample notebook already exists");
            return;
        }

        log.info("Creating sample notebook with demo content");

        // Create notebook and chapter
        var notebook = notebookService.createNotebook("Sample Notebook");
        var chapter = notebookService.createChapter(notebook.id(), "Getting Started");

        // Create a page with mixed content (this also updates the chapter's pageIds)
        var page = pageService.createPage(notebook.id(), chapter.id(), "Welcome to Drawboard");

        // Add text element
        TextElement textElement = new TextElement(
            UUID.randomUUID().toString(),
            50,
            50,
            400,
            120,
            "<h1>Welcome to Drawboard!</h1><p>This is a <strong>rich text</strong> element. You can format text with <em>bold</em>, <em>italic</em>, and different heading levels.</p>",
            0
        );

        // Add another text element
        TextElement textElement2 = new TextElement(
            UUID.randomUUID().toString(),
            50,
            200,
            500,
            100,
            "<h2>Features</h2><ul><li>Free-form canvas pages</li><li>Rich text with formatting</li><li>Images and drawings</li><li>Hierarchical organization</li></ul>",
            1
        );

        // Add a drawing element (simple arrow-like shape)
        List<Point> arrowPoints = List.of(
            new Point(0, 0),
            new Point(100, 0),
            new Point(100, -10),
            new Point(120, 5),
            new Point(100, 20),
            new Point(100, 10),
            new Point(0, 10),
            new Point(0, 0)
        );

        DrawPath arrowPath = new DrawPath("#0066CC", 2.0, arrowPoints);
        DrawingElement drawingElement = new DrawingElement(
            UUID.randomUUID().toString(),
            50,
            350,
            List.of(arrowPath),
            2
        );

        // Add a highlight drawing
        List<Point> highlightPoints = List.of(
            new Point(0, 0),
            new Point(200, 0),
            new Point(200, 20),
            new Point(0, 20)
        );

        DrawPath highlightPath = new DrawPath("#FFFF00", 20.0, highlightPoints);
        DrawingElement highlight = new DrawingElement(
            UUID.randomUUID().toString(),
            100,
            85,
            List.of(highlightPath),
            0  // Behind text
        );

        // Update page with elements
        List<com.drawboard.domain.elements.CanvasElement> elements = new ArrayList<>();
        elements.add(highlight);
        elements.add(textElement);
        elements.add(textElement2);
        elements.add(drawingElement);

        Page updatedPage = new Page(
            page.id(),
            page.name(),
            page.created(),
            Instant.now(),
            elements
        );

        pageService.savePage(notebook.id(), chapter.id(), updatedPage);

        log.info("Sample notebook created successfully");
    }
}
