package com.drawboard.storage;

import com.drawboard.domain.Chapter;
import com.drawboard.domain.Notebook;
import com.drawboard.domain.Page;
import io.avaje.jsonb.Jsonb;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Handles file-based storage for notebooks, chapters, and pages.
 * Storage structure:
 * ~/Documents/DrawboardNotebooks/
 *   notebook-{id}/
 *     notebook.json
 *     chapter-{id}/
 *       chapter.json
 *       page-{id}/
 *         page.json
 *         image-1.png
 */
@Singleton
public class FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final String BASE_DIR_NAME = "DrawboardNotebooks";

    protected final Path baseDirectory;
    private final Jsonb jsonb;

    @Inject
    public FileStorageService(Jsonb jsonb) {
        this(jsonb, Path.of(System.getProperty("user.home"), "Documents", BASE_DIR_NAME));
    }

    // Public constructor for testing with custom base directory
    public FileStorageService(Jsonb jsonb, Path baseDirectory) {
        this.jsonb = jsonb;
        this.baseDirectory = baseDirectory;
        initializeBaseDirectory();
    }

    private void initializeBaseDirectory() {
        try {
            if (!Files.exists(baseDirectory)) {
                Files.createDirectories(baseDirectory);
                log.info("Created base storage directory: {}", baseDirectory);
            }
        } catch (IOException e) {
            log.error("Failed to create base directory: {}", baseDirectory, e);
            throw new StorageException("Cannot initialize storage directory", e);
        }
    }

    // ==================== Notebook Operations ====================

    public List<Notebook> loadAllNotebooks() {
        try (Stream<Path> paths = Files.list(baseDirectory)) {
            return paths
                .filter(Files::isDirectory)
                .filter(p -> p.getFileName().toString().startsWith("notebook-"))
                .map(this::loadNotebookFromDirectory)
                .filter(notebook -> notebook != null)
                .toList();
        } catch (IOException e) {
            log.error("Failed to list notebooks", e);
            return List.of();
        }
    }

    private Notebook loadNotebookFromDirectory(Path notebookDir) {
        Path notebookFile = notebookDir.resolve("notebook.json");
        if (!Files.exists(notebookFile)) {
            log.warn("Notebook file not found: {}", notebookFile);
            return null;
        }

        try {
            String json = Files.readString(notebookFile);
            return jsonb.type(Notebook.class).fromJson(json);
        } catch (IOException e) {
            log.error("Failed to read notebook: {}", notebookFile, e);
            return null;
        }
    }

    public Notebook loadNotebook(String notebookId) {
        Path notebookDir = getNotebookDirectory(notebookId);
        return loadNotebookFromDirectory(notebookDir);
    }

    public void saveNotebook(Notebook notebook) {
        Path notebookDir = getNotebookDirectory(notebook.id());

        try {
            Files.createDirectories(notebookDir);
            Path notebookFile = notebookDir.resolve("notebook.json");
            String json = jsonb.type(Notebook.class).toJson(notebook);
            Files.writeString(notebookFile, json);
            log.info("Saved notebook: {} ({})", notebook.name(), notebook.id());
        } catch (IOException e) {
            log.error("Failed to save notebook: {}", notebook.id(), e);
            throw new StorageException("Cannot save notebook", e);
        }
    }

    public void deleteNotebook(String notebookId) {
        Path notebookDir = getNotebookDirectory(notebookId);

        try {
            deleteDirectoryRecursively(notebookDir);
            log.info("Deleted notebook: {}", notebookId);
        } catch (IOException e) {
            log.error("Failed to delete notebook: {}", notebookId, e);
            throw new StorageException("Cannot delete notebook", e);
        }
    }

    // ==================== Chapter Operations ====================

    public Chapter loadChapter(String notebookId, String chapterId) {
        Path chapterFile = getChapterDirectory(notebookId, chapterId).resolve("chapter.json");

        if (!Files.exists(chapterFile)) {
            log.warn("Chapter file not found: {}", chapterFile);
            return null;
        }

        try {
            String json = Files.readString(chapterFile);
            return jsonb.type(Chapter.class).fromJson(json);
        } catch (IOException e) {
            log.error("Failed to read chapter: {}", chapterFile, e);
            return null;
        }
    }

    public void saveChapter(String notebookId, Chapter chapter) {
        Path chapterDir = getChapterDirectory(notebookId, chapter.id());

        try {
            Files.createDirectories(chapterDir);
            Path chapterFile = chapterDir.resolve("chapter.json");
            String json = jsonb.type(Chapter.class).toJson(chapter);
            Files.writeString(chapterFile, json);
            log.info("Saved chapter: {} ({})", chapter.name(), chapter.id());
        } catch (IOException e) {
            log.error("Failed to save chapter: {}", chapter.id(), e);
            throw new StorageException("Cannot save chapter", e);
        }
    }

    public void deleteChapter(String notebookId, String chapterId) {
        Path chapterDir = getChapterDirectory(notebookId, chapterId);

        try {
            deleteDirectoryRecursively(chapterDir);
            log.info("Deleted chapter: {}", chapterId);
        } catch (IOException e) {
            log.error("Failed to delete chapter: {}", chapterId, e);
            throw new StorageException("Cannot delete chapter", e);
        }
    }

    // ==================== Page Operations ====================

    public Page loadPage(String notebookId, String chapterId, String pageId) {
        Path pageFile = getPageDirectory(notebookId, chapterId, pageId).resolve("page.json");

        if (!Files.exists(pageFile)) {
            log.warn("Page file not found: {}", pageFile);
            return null;
        }

        try {
            String json = Files.readString(pageFile);
            return jsonb.type(Page.class).fromJson(json);
        } catch (IOException e) {
            log.error("Failed to read page: {}", pageFile, e);
            return null;
        }
    }

    public void savePage(String notebookId, String chapterId, Page page) {
        Path pageDir = getPageDirectory(notebookId, chapterId, page.id());

        try {
            Files.createDirectories(pageDir);
            Path pageFile = pageDir.resolve("page.json");
            String json = jsonb.type(Page.class).toJson(page);
            Files.writeString(pageFile, json);
            log.debug("Saved page: {}", page.id());
        } catch (IOException e) {
            log.error("Failed to save page: {}", page.id(), e);
            throw new StorageException("Cannot save page", e);
        }
    }

    public void deletePage(String notebookId, String chapterId, String pageId) {
        Path pageDir = getPageDirectory(notebookId, chapterId, pageId);

        try {
            deleteDirectoryRecursively(pageDir);
            log.info("Deleted page: {}", pageId);
        } catch (IOException e) {
            log.error("Failed to delete page: {}", pageId, e);
            throw new StorageException("Cannot delete page", e);
        }
    }

    // ==================== Image Operations ====================

    public void saveImage(String notebookId, String chapterId, String pageId,
                         String filename, byte[] imageData) {
        Path pageDir = getPageDirectory(notebookId, chapterId, pageId);
        Path imageFile = pageDir.resolve(filename);

        try {
            Files.createDirectories(pageDir);
            Files.write(imageFile, imageData);
            log.debug("Saved image: {}", filename);
        } catch (IOException e) {
            log.error("Failed to save image: {}", filename, e);
            throw new StorageException("Cannot save image", e);
        }
    }

    public byte[] loadImage(String notebookId, String chapterId, String pageId,
                           String filename) {
        Path imageFile = getPageDirectory(notebookId, chapterId, pageId).resolve(filename);

        if (!Files.exists(imageFile)) {
            log.warn("Image file not found: {}", imageFile);
            return null;
        }

        try {
            return Files.readAllBytes(imageFile);
        } catch (IOException e) {
            log.error("Failed to read image: {}", imageFile, e);
            return null;
        }
    }

    public void deleteImage(String notebookId, String chapterId, String pageId,
                           String filename) {
        Path imageFile = getPageDirectory(notebookId, chapterId, pageId).resolve(filename);

        if (!Files.exists(imageFile)) {
            log.warn("Image file not found for deletion: {}", imageFile);
            return;
        }

        try {
            Files.delete(imageFile);
            log.info("Deleted image: {}", filename);
        } catch (IOException e) {
            log.error("Failed to delete image: {}", filename, e);
            throw new StorageException("Cannot delete image", e);
        }
    }

    // ==================== Path Helpers ====================

    private Path getNotebookDirectory(String notebookId) {
        return baseDirectory.resolve("notebook-" + notebookId);
    }

    private Path getChapterDirectory(String notebookId, String chapterId) {
        return getNotebookDirectory(notebookId).resolve("chapter-" + chapterId);
    }

    private Path getPageDirectory(String notebookId, String chapterId, String pageId) {
        return getChapterDirectory(notebookId, chapterId).resolve("page-" + pageId);
    }

    private void deleteDirectoryRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted((a, b) -> -a.compareTo(b))  // Reverse order to delete children first
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        log.warn("Failed to delete: {}", path, e);
                    }
                });
        }
    }

    public Path getBaseDirectory() {
        return baseDirectory;
    }
}
