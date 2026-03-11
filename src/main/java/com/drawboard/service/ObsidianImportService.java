package com.drawboard.service;

import com.drawboard.domain.Chapter;
import com.drawboard.domain.Notebook;
import com.drawboard.domain.Page;
import com.drawboard.domain.elements.ImageElement;
import com.drawboard.domain.elements.TextElement;
import com.drawboard.storage.FileStorageService;
import com.drawboard.util.MarkdownFormatter;
import com.drawboard.util.MarkdownFormatter.ImageReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for importing Obsidian vaults into Drawboard.
 *
 * <p>Mapping:
 * <ul>
 *   <li>Vault → Notebook</li>
 *   <li>Root .md files → "Root" Chapter</li>
 *   <li>Subdirectories → Chapters (flattened, no nesting)</li>
 *   <li>.md files → Pages with single TextElement</li>
 *   <li>.canvas files → Pages with multiple elements</li>
 *   <li>Images → Only imported if referenced from .md files</li>
 * </ul>
 */
@Singleton
public class ObsidianImportService {
    private static final Logger log = LoggerFactory.getLogger(ObsidianImportService.class);
    private static final String ROOT_CHAPTER_NAME = "Root";
    private static final String OBSIDIAN_DIR = ".obsidian";

    private final NotebookService notebookService;
    private final PageService pageService;
    private final FileStorageService storage;
    private final ObjectMapper objectMapper;

    public ObsidianImportService(NotebookService notebookService,
                                PageService pageService,
                                FileStorageService storage) {
        this.notebookService = notebookService;
        this.pageService = pageService;
        this.storage = storage;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Import an Obsidian vault as a new notebook.
     *
     * @param vaultPath Path to the Obsidian vault directory
     * @param notebookName Name for the new notebook (if null, uses vault directory name)
     * @return The created notebook
     * @throws IOException if vault cannot be read
     */
    public Notebook importVault(Path vaultPath, String notebookName) throws IOException {
        if (!Files.isDirectory(vaultPath)) {
            throw new IllegalArgumentException("Vault path is not a directory: " + vaultPath);
        }

        // Use vault directory name if no notebook name provided
        if (notebookName == null || notebookName.isBlank()) {
            notebookName = vaultPath.getFileName().toString();
        }

        log.info("Starting import of Obsidian vault: {} -> {}", vaultPath, notebookName);

        // Scan vault structure
        VaultStructure structure = scanVaultStructure(vaultPath);
        log.info("Found {} root files, {} subdirectories",
                structure.rootFiles.size(), structure.subdirectories.size());

        // Create notebook
        Notebook notebook = notebookService.createNotebook(notebookName);
        String notebookId = notebook.id();

        // Import root files into "Root" chapter
        if (!structure.rootFiles.isEmpty()) {
            Chapter rootChapter = notebookService.createChapter(notebookId, ROOT_CHAPTER_NAME);
            importFilesIntoChapter(vaultPath, structure.rootFiles, notebookId, rootChapter.id());
        }

        // Import each subdirectory as a chapter
        for (Map.Entry<String, List<Path>> entry : structure.subdirectories.entrySet()) {
            String chapterName = entry.getKey();
            List<Path> files = entry.getValue();

            if (!files.isEmpty()) {
                log.info("Creating chapter '{}' with {} files", chapterName, files.size());
                Chapter chapter = notebookService.createChapter(notebookId, chapterName);
                importFilesIntoChapter(vaultPath, files, notebookId, chapter.id());
            } else {
                log.debug("Skipping empty directory: {}", chapterName);
            }
        }

        log.info("Successfully imported vault as notebook: {}", notebookName);
        return notebookService.getNotebook(notebookId);
    }

    /**
     * Scan the vault directory structure.
     */
    private VaultStructure scanVaultStructure(Path vaultPath) throws IOException {
        VaultStructure structure = new VaultStructure();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(vaultPath)) {
            for (Path entry : stream) {
                String filename = entry.getFileName().toString();

                // Skip .obsidian directory
                if (filename.equals(OBSIDIAN_DIR)) {
                    continue;
                }

                if (Files.isDirectory(entry)) {
                    // Scan subdirectory for files (non-recursive for MVP)
                    List<Path> filesInDir = scanDirectoryFiles(entry);
                    if (!filesInDir.isEmpty()) {
                        structure.subdirectories.put(filename, filesInDir);
                    }
                } else if (Files.isRegularFile(entry)) {
                    // Check if it's a .md or .canvas file
                    if (filename.endsWith(".md") || filename.endsWith(".canvas")) {
                        structure.rootFiles.add(entry);
                    }
                }
            }
        }

        return structure;
    }

    /**
     * Scan a directory for .md and .canvas files (non-recursive).
     */
    private List<Path> scanDirectoryFiles(Path directory) throws IOException {
        List<Path> files = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    String filename = entry.getFileName().toString();
                    if (filename.endsWith(".md") || filename.endsWith(".canvas")) {
                        files.add(entry);
                    }
                }
            }
        }

        return files;
    }

    /**
     * Import a list of files into a chapter.
     */
    private void importFilesIntoChapter(Path vaultPath, List<Path> files,
                                       String notebookId, String chapterId) {
        log.debug("Importing {} files into chapter {}", files.size(), chapterId);

        int successCount = 0;
        for (Path file : files) {
            try {
                String filename = file.getFileName().toString();

                if (filename.endsWith(".md")) {
                    importMarkdownFile(vaultPath, file, notebookId, chapterId);
                    successCount++;
                } else if (filename.endsWith(".canvas")) {
                    importCanvasFile(vaultPath, file, notebookId, chapterId);
                    successCount++;
                }
            } catch (Exception e) {
                log.error("Failed to import file: {}", file, e);
            }
        }

        log.info("Successfully imported {}/{} files into chapter {}", successCount, files.size(), chapterId);
    }

    /**
     * Import a markdown file as a page with a single text element.
     */
    private void importMarkdownFile(Path vaultPath, Path mdFile,
                                    String notebookId, String chapterId) throws IOException {
        String filename = mdFile.getFileName().toString();
        String pageName = filename.replace(".md", "");

        log.debug("Importing markdown file: {}", filename);

        // Read markdown content
        String markdown = Files.readString(mdFile);

        // Extract image references
        List<ImageReference> imageRefs = MarkdownFormatter.extractImages(markdown);

        // Remove image syntax from markdown
        markdown = MarkdownFormatter.removeImages(markdown);

        // Convert markdown to HTML
        String html = MarkdownFormatter.toHtml(markdown);

        // Create text element
        String elementId = UUID.randomUUID().toString();
        TextElement textElement = new TextElement(
            elementId,
            50.0,   // x
            50.0,   // y
            600.0,  // width
            200.0,  // height
            html,
            0       // zIndex
        );

        // Create page first (so we have a page ID for image storage)
        String pageId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        List<com.drawboard.domain.elements.CanvasElement> elements = new ArrayList<>();
        elements.add(textElement);

        Page page = new Page(pageId, pageName, now, now, elements);
        storage.savePage(notebookId, chapterId, page);

        // Now import images (they need the page ID to be stored correctly)
        List<ImageElement> imageElements = new ArrayList<>();
        double imageX = 50;
        double imageY = 300; // Position images below text

        for (ImageReference imageRef : imageRefs) {
            try {
                Path imagePath = resolveImagePath(vaultPath, mdFile.getParent(), imageRef.path());
                if (imagePath != null && Files.exists(imagePath)) {
                    ImageElement imageElement = importImage(imagePath, notebookId, chapterId,
                                                           pageId, imageX, imageY);
                    imageElements.add(imageElement);
                    imageX += 200; // Space images horizontally
                }
            } catch (Exception e) {
                log.warn("Failed to import image: {}", imageRef.path(), e);
            }
        }

        // Update page with images if any were imported
        if (!imageElements.isEmpty()) {
            elements.addAll(imageElements);
            Page updatedPage = new Page(pageId, pageName, now, now, elements);
            storage.savePage(notebookId, chapterId, updatedPage);
        }

        // Update chapter to include page
        Chapter chapter = notebookService.getChapter(notebookId, chapterId);
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
        notebookService.updateChapterInNotebook(notebookId, updatedChapter);

        log.info("Imported markdown page: {} with {} images", pageName, imageElements.size());
    }

    /**
     * Import an Obsidian canvas file as a page with multiple elements.
     */
    private void importCanvasFile(Path vaultPath, Path canvasFile,
                                  String notebookId, String chapterId) throws IOException {
        String filename = canvasFile.getFileName().toString();
        String pageName = filename.replace(".canvas", "");

        log.debug("Importing canvas file: {}", filename);

        // Read and parse canvas JSON
        String json = Files.readString(canvasFile);
        JsonNode root = objectMapper.readTree(json);
        JsonNode nodes = root.get("nodes");

        if (nodes == null || !nodes.isArray()) {
            log.warn("Canvas file has no nodes: {}", filename);
            return;
        }

        // Convert canvas nodes to elements
        List<com.drawboard.domain.elements.CanvasElement> elements = new ArrayList<>();

        // Find min x/y to center the canvas
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;

        for (JsonNode node : nodes) {
            double x = node.get("x").asDouble();
            double y = node.get("y").asDouble();
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
        }

        // Offset to center canvas (avoid negative coordinates)
        double offsetX = minX < 0 ? -minX + 50 : 50;
        double offsetY = minY < 0 ? -minY + 50 : 50;

        for (JsonNode node : nodes) {
            try {
                String type = node.get("type").asText();

                if ("text".equals(type)) {
                    String elementId = UUID.randomUUID().toString();
                    double x = node.get("x").asDouble() + offsetX;
                    double y = node.get("y").asDouble() + offsetY;
                    double width = node.get("width").asDouble();
                    double height = node.get("height").asDouble();
                    String text = node.get("text").asText();

                    // Convert text content (might be markdown)
                    String html = MarkdownFormatter.toHtml(text);

                    TextElement textElement = new TextElement(
                        elementId, x, y, width, height, html, 0
                    );
                    elements.add(textElement);
                }
                // Could add support for other node types (file, link, etc.) in the future
            } catch (Exception e) {
                log.warn("Failed to import canvas node", e);
            }
        }

        if (elements.isEmpty()) {
            log.warn("No elements imported from canvas: {}", filename);
            return;
        }

        // Create page
        String pageId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Page page = new Page(pageId, pageName, now, now, elements);
        storage.savePage(notebookId, chapterId, page);

        // Update chapter to include page
        Chapter chapter = notebookService.getChapter(notebookId, chapterId);
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
        notebookService.updateChapterInNotebook(notebookId, updatedChapter);

        log.info("Imported canvas page: {} with {} elements", pageName, elements.size());
    }

    /**
     * Import an image file, copy it to the page directory, and return the ImageElement.
     */
    private ImageElement importImage(Path imagePath, String notebookId, String chapterId,
                                    String pageId, double x, double y) throws IOException {
        String elementId = UUID.randomUUID().toString();

        // Generate unique filename
        String originalFilename = imagePath.getFileName().toString();
        String extension = getFileExtension(originalFilename);
        String filename = "image-" + UUID.randomUUID() + extension;

        // Read image data
        byte[] imageData = Files.readAllBytes(imagePath);

        // Save image to storage
        storage.saveImage(notebookId, chapterId, pageId, filename, imageData);

        log.debug("Imported image: {} -> {}", originalFilename, filename);

        return new ImageElement(elementId, x, y, 0.0, 0.0, filename, 0);
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot);
        }
        return "";
    }

    /**
     * Resolve an image path from markdown reference.
     * Handles both relative paths and [[wikilink]] style references.
     */
    private Path resolveImagePath(Path vaultPath, Path mdFileDir, String imageRef) {
        // Remove [[]] if present
        if (imageRef.startsWith("[[") && imageRef.endsWith("]]")) {
            imageRef = imageRef.substring(2, imageRef.length() - 2);
        }

        // Try relative to markdown file
        Path relativePath = mdFileDir != null ? mdFileDir.resolve(imageRef) : null;
        if (relativePath != null && Files.exists(relativePath)) {
            return relativePath;
        }

        // Try relative to vault root
        Path vaultRootPath = vaultPath.resolve(imageRef);
        if (Files.exists(vaultRootPath)) {
            return vaultRootPath;
        }

        // Try finding the file anywhere in the vault (Obsidian resolves by filename)
        try {
            String imageFilename = Path.of(imageRef).getFileName().toString();
            return Files.walk(vaultPath)
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().equals(imageFilename))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            log.warn("Failed to search for image: {}", imageRef, e);
            return null;
        }
    }

    /**
     * Internal structure to hold vault scan results.
     */
    private static class VaultStructure {
        List<Path> rootFiles = new ArrayList<>();
        Map<String, List<Path>> subdirectories = new LinkedHashMap<>();
    }
}
