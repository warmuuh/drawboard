# Drawboard Implementation Plan

This document outlines the step-by-step implementation plan for the Drawboard notebook application.

## Overview

Drawboard is a desktop notebook app with free-form canvas pages supporting rich text, images, and freehand drawings. Content is organized in a hierarchy: **Notebooks → Chapters → Pages**.

**Technology**: Java 25, JavaFX 23, Avaje Inject (DI), Avaje JsonB (serialization), Maven

**Canvas Strategy**: Hybrid approach using JavaFX Canvas for drawings + JavaFX Nodes (WebView/ImageView) for text/images

---

## Phase 1: Domain Models (Foundation)

### 1.1 Create Core Domain Classes

**Goal**: Define the data structures representing notebooks, chapters, pages, and canvas elements.

**Files to Create**:
- `src/main/java/com/drawboard/domain/Notebook.java`
- `src/main/java/com/drawboard/domain/Chapter.java`
- `src/main/java/com/drawboard/domain/Page.java`

**Implementation Details**:

**Notebook**:
```java
@Json
public record Notebook(
    String id,              // UUID
    String name,            // Display name
    Instant created,
    Instant modified,
    List<Chapter> chapters
) {}
```

**Chapter**:
```java
@Json
public record Chapter(
    String id,              // UUID
    String name,            // Display name
    Instant created,
    Instant modified,
    List<Page> pages        // Or just List<String> pageIds?
) {}
```

**Page**:
```java
@Json
public record Page(
    String id,              // UUID
    String name,            // Display name (optional)
    Instant created,
    Instant modified,
    List<CanvasElement> elements
) {}
```

**Questions**:
- Should `Chapter` contain full `Page` objects or just page IDs (since pages are stored in separate directories)?
- Do pages need a "name" or just rely on their position in the chapter?

### 1.2 Define Canvas Element Types

**Goal**: Create a sealed interface for type-safe canvas elements.

**Files to Create**:
- `src/main/java/com/drawboard/domain/elements/CanvasElement.java`
- `src/main/java/com/drawboard/domain/elements/TextElement.java`
- `src/main/java/com/drawboard/domain/elements/ImageElement.java`
- `src/main/java/com/drawboard/domain/elements/DrawingElement.java`

**Implementation Details**:

**CanvasElement (sealed interface)**:
```java
@Json
public sealed interface CanvasElement
    permits TextElement, ImageElement, DrawingElement {
    String id();
    double x();
    double y();
    int zIndex();
}
```

**TextElement**:
```java
@Json
public record TextElement(
    String id,
    double x,
    double y,
    double width,
    double height,
    String htmlContent,     // Rich text as HTML
    int zIndex
) implements CanvasElement {}
```

**ImageElement**:
```java
@Json
public record ImageElement(
    String id,
    double x,
    double y,
    double width,
    double height,
    String filename,        // Relative path in page directory
    int zIndex
) implements CanvasElement {}
```

**DrawingElement**:
```java
@Json
public record DrawingElement(
    String id,
    double x,
    double y,
    List<DrawPath> paths,   // Stroke data
    int zIndex
) implements CanvasElement {}

@Json
public record DrawPath(
    String color,           // Hex color
    double strokeWidth,
    List<Point> points      // List of [x, y] coordinates
) {}

@Json
public record Point(double x, double y) {}
```

**Questions**:
- For rich text, should we use HTML or a custom format? (HTML with WebView is simplest)
- Do we need rotation/transparency for elements?
- Should drawings have a bounding box or be positioned by their first point?

### 1.3 Update module-info.java

**Goal**: Export and open domain packages for JSON serialization.

**File to Edit**: `src/main/java/module-info.java`

Add:
```java
exports com.drawboard.domain;
exports com.drawboard.domain.elements;
opens com.drawboard.domain to io.avaje.jsonb;
opens com.drawboard.domain.elements to io.avaje.jsonb;
```

---

## Phase 2: Storage Layer (Persistence)

### 2.1 Define Storage Structure

**Goal**: Implement file-based storage for notebooks.

**Directory Structure** (Example):
```
~/Documents/DrawboardNotebooks/
├── notebook-{uuid}/
│   ├── notebook.json               # Notebook metadata
│   ├── chapter-{uuid}/
│   │   ├── chapter.json            # Chapter metadata
│   │   ├── page-{uuid}/
│   │   │   ├── page.json           # Page structure
│   │   │   ├── image-1.png
│   │   │   └── image-2.jpg
│   │   └── page-{uuid}/
│   └── chapter-{uuid}/
```

**Questions**:
- Should the base directory be configurable or hardcoded to `~/Documents/DrawboardNotebooks/`?
- Use UUIDs in directory names or human-readable slugs (e.g., `Work-Notebook/`)?

### 2.2 Create FileStorageService

**Goal**: Handle reading/writing notebooks to disk.

**File to Create**: `src/main/java/com/drawboard/storage/FileStorageService.java`

**Implementation Details**:

```java
@Singleton
public class FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private final Path baseDirectory;
    private final Jsonb jsonb;

    @Inject
    public FileStorageService(Jsonb jsonb) {
        this.jsonb = jsonb;
        this.baseDirectory = Path.of(System.getProperty("user.home"), "Documents", "DrawboardNotebooks");
        initializeBaseDirectory();
    }

    public List<Notebook> loadAllNotebooks() { ... }
    public Notebook loadNotebook(String notebookId) { ... }
    public void saveNotebook(Notebook notebook) { ... }
    public void deleteNotebook(String notebookId) { ... }

    public Chapter loadChapter(String notebookId, String chapterId) { ... }
    public void saveChapter(String notebookId, Chapter chapter) { ... }

    public Page loadPage(String notebookId, String chapterId, String pageId) { ... }
    public void savePage(String notebookId, String chapterId, Page page) { ... }

    public void saveImage(String notebookId, String chapterId, String pageId,
                         String filename, byte[] imageData) { ... }
    public byte[] loadImage(String notebookId, String chapterId, String pageId,
                           String filename) { ... }
}
```

**Questions**:
- Should we cache loaded notebooks in memory or always read from disk?
- How to handle concurrent access (multiple Drawboard instances)? File locking?

### 2.3 JSON Serialization

**Goal**: Ensure Avaje JsonB can serialize/deserialize domain models.

**Files to Create**:
- None (Avaje handles this via `@Json` annotations)

**Testing**: Create unit tests to verify serialization works correctly.

**Questions**:
- Any custom serialization logic needed (e.g., date formats)?

---

## Phase 3: Service Layer (Business Logic)

### 3.1 Create NotebookService

**Goal**: High-level operations for managing notebooks and chapters.

**File to Create**: `src/main/java/com/drawboard/service/NotebookService.java`

**Implementation Details**:

```java
@Singleton
public class NotebookService {
    private final FileStorageService storage;

    @Inject
    public NotebookService(FileStorageService storage) {
        this.storage = storage;
    }

    public List<Notebook> getAllNotebooks() { ... }
    public Notebook createNotebook(String name) { ... }
    public void renameNotebook(String notebookId, String newName) { ... }
    public void deleteNotebook(String notebookId) { ... }

    public Chapter createChapter(String notebookId, String name) { ... }
    public void renameChapter(String notebookId, String chapterId, String newName) { ... }
    public void deleteChapter(String notebookId, String chapterId) { ... }
}
```

### 3.2 Create PageService

**Goal**: Operations for managing pages and canvas elements.

**File to Create**: `src/main/java/com/drawboard/service/PageService.java`

**Implementation Details**:

```java
@Singleton
public class PageService {
    private final FileStorageService storage;

    @Inject
    public PageService(FileStorageService storage) {
        this.storage = storage;
    }

    public Page createPage(String notebookId, String chapterId) { ... }
    public Page loadPage(String notebookId, String chapterId, String pageId) { ... }
    public void savePage(String notebookId, String chapterId, Page page) { ... }
    public void deletePage(String notebookId, String chapterId, String pageId) { ... }

    // Element operations
    public void addElement(String notebookId, String chapterId, String pageId,
                          CanvasElement element) { ... }
    public void updateElement(String notebookId, String chapterId, String pageId,
                             CanvasElement element) { ... }
    public void deleteElement(String notebookId, String chapterId, String pageId,
                             String elementId) { ... }

    // Image handling
    public void addImage(String notebookId, String chapterId, String pageId,
                        File imageFile, double x, double y) { ... }
}
```

**Questions**:
- Should services maintain in-memory state (currently opened notebook/page) or always pass IDs?
- How to handle auto-save? Debouncing in the service layer or UI layer?

---

## Phase 4: UI Shell (Basic Layout)

### 4.1 Create Main Window Layout

**Goal**: Build the main application window with navigation tree (left) and canvas area (right).

**Files to Create**:
- `src/main/resources/fxml/MainWindow.fxml`
- `src/main/java/com/drawboard/ui/MainWindowController.java`

**Layout Structure**:
```
+--------------------------------------------------+
| Menu Bar (File, Edit, View, Help)               |
+--------------------------------------------------+
| Toolbar (New, Save, Undo, Redo, Tools)          |
+--------------------------------------------------+
|  Navigation  |  Canvas Editor                    |
|  Tree        |                                   |
|  (200px)     |  (Expandable)                     |
|              |                                   |
|  Notebooks   |                                   |
|  ├─ Work     |                                   |
|  │  ├─ Ch1   |                                   |
|  │  │  ├─ P1 |                                   |
|  │  │  └─ P2 |                                   |
|  └─ Personal |                                   |
+--------------------------------------------------+
| Status Bar (Save status, Page info)             |
+--------------------------------------------------+
```

**Implementation**:
- Use `SplitPane` for navigation tree + canvas
- Use `TreeView` for notebook hierarchy
- Use `BorderPane` for overall layout

### 4.2 Create Navigation Tree

**Goal**: Display and interact with the notebook hierarchy.

**File to Create**: `src/main/java/com/drawboard/ui/NavigationTreeController.java`

**Features**:
- Load notebooks from `NotebookService`
- Display as tree structure
- Context menu: New Notebook/Chapter/Page, Rename, Delete
- Double-click page to open in canvas

**Questions**:
- Should the tree be its own FXML component or part of MainWindow.fxml?
- How to handle tree item selection (event handling)?

### 4.3 Create Canvas Editor Placeholder

**Goal**: Create an empty canvas area that will be populated later.

**File to Create**: `src/main/java/com/drawboard/ui/CanvasEditorController.java`

**Implementation**:
- For now, just a `StackPane` or `AnchorPane` with a label "Canvas will go here"
- Wire up to receive page selection events from navigation tree

### 4.4 Update DrawboardApplication

**Goal**: Load the main window on startup.

**File to Edit**: `src/main/java/com/drawboard/app/DrawboardApplication.java`

**Implementation**:
```java
@Override
public void start(Stage primaryStage) {
    log.info("Starting Drawboard UI");

    FXMLLoader loader = new FXMLLoader(
        getClass().getResource("/fxml/MainWindow.fxml")
    );
    Parent root = loader.load();

    Scene scene = new Scene(root, 1200, 800);
    primaryStage.setTitle("Drawboard");
    primaryStage.setScene(scene);
    primaryStage.show();
}
```

**Questions**:
- How to inject services into FXML controllers? Manual injection or factory?

### 4.5 Update module-info.java

**File to Edit**: `src/main/java/module-info.java`

Add:
```java
exports com.drawboard.ui;
opens com.drawboard.ui to javafx.fxml;
```

---

## Phase 5: Canvas Implementation (Rendering)

### 5.1 Design Canvas Architecture

**Goal**: Implement hybrid canvas approach.

**Architecture**:
```
CanvasEditor (Pane)
├── Canvas (for drawings) - bottom layer
└── ElementsPane (Pane) - top layer
    ├── WebView (rich text element 1)
    ├── WebView (rich text element 2)
    ├── ImageView (image element 1)
    └── ...
```

**Questions**:
- Should we use one WebView per text element or a single WebView with multiple contentEditable divs?
- How to handle z-index ordering between Canvas drawings and Nodes?

### 5.2 Implement TextElement Rendering

**Goal**: Render rich text elements on the canvas.

**File to Create**: `src/main/java/com/drawboard/canvas/renderers/TextElementRenderer.java`

**Implementation**:
- Use `WebView` with HTML content from `TextElement.htmlContent`
- Position at (x, y) with width/height
- Enable contentEditable for in-place editing
- Handle focus/blur to update the model

**Rich Text Toolbar**:
- Bold, Italic, Underline
- Heading levels (H1, H2, H3)
- Font size
- Text color

**Questions**:
- Should we use a lightweight rich text library or stick with WebView?
- How to handle text overflow (scrollable or auto-expand height)?

### 5.3 Implement ImageElement Rendering

**Goal**: Render images on the canvas.

**File to Create**: `src/main/java/com/drawboard/canvas/renderers/ImageElementRenderer.java`

**Implementation**:
- Use `ImageView` to display image from file
- Position at (x, y) with width/height
- Load image via `FileStorageService.loadImage()`
- Handle image not found gracefully

### 5.4 Implement DrawingElement Rendering

**Goal**: Render freehand drawings on the canvas.

**File to Create**: `src/main/java/com/drawboard/canvas/renderers/DrawingElementRenderer.java`

**Implementation**:
- Draw paths on JavaFX `Canvas` using `GraphicsContext`
- Iterate through `DrawPath` objects and render each stroke
- Use `strokeLine()` or `strokePolyline()` for paths
- Apply color and stroke width

**Questions**:
- Should drawings be on a separate canvas per element or all on one shared canvas?
- How to handle erasing (separate eraser tool or just delete element)?

### 5.5 Implement Canvas Manager

**Goal**: Coordinate rendering of all elements on the canvas.

**File to Create**: `src/main/java/com/drawboard/canvas/CanvasManager.java`

**Implementation**:
```java
@Singleton
public class CanvasManager {
    private Pane canvasContainer;
    private Canvas drawingCanvas;
    private Pane elementsPane;

    public void loadPage(Page page) {
        clear();
        for (CanvasElement element : page.elements()) {
            renderElement(element);
        }
    }

    private void renderElement(CanvasElement element) {
        switch (element) {
            case TextElement te -> renderText(te);
            case ImageElement ie -> renderImage(ie);
            case DrawingElement de -> renderDrawing(de);
        }
    }

    public void addElement(CanvasElement element) { ... }
    public void updateElement(CanvasElement element) { ... }
    public void removeElement(String elementId) { ... }
    private void clear() { ... }
}
```

---

## Phase 6: Interactivity (User Actions)

### 6.1 Implement Selection Tool

**Goal**: Allow users to select and highlight canvas elements.

**File to Create**: `src/main/java/com/drawboard/canvas/tools/SelectionTool.java`

**Features**:
- Click on element to select
- Show selection border (rectangle with resize handles)
- Keyboard: Delete key to remove element
- Click on empty canvas to deselect

**Questions**:
- Multi-select with Ctrl/Cmd+click?
- Should selection be visual only or affect a model state?

### 6.2 Implement Move and Resize

**Goal**: Drag elements to reposition and resize them.

**File to Create**: `src/main/java/com/drawboard/canvas/tools/TransformTool.java`

**Features**:
- Drag element to move (update x, y)
- Drag corner handles to resize (update width, height)
- Maintain aspect ratio for images (Shift+drag?)
- Update model on mouse release

### 6.3 Implement Drawing Tools

**Goal**: Freehand drawing with pen/highlighter.

**Files to Create**:
- `src/main/java/com/drawboard/canvas/tools/PenTool.java`
- `src/main/java/com/drawboard/canvas/tools/HighlighterTool.java`

**Features**:
- Mouse press to start drawing
- Mouse drag to draw path
- Mouse release to finish and create `DrawingElement`
- Color picker and stroke width slider in toolbar

**Questions**:
- Smoothing/interpolation for paths or raw points?
- Separate eraser tool or use pen with "erase" mode?

### 6.4 Implement Text and Image Insertion

**Goal**: Add new text boxes and images to the canvas.

**Files to Create**:
- `src/main/java/com/drawboard/canvas/tools/TextTool.java`
- `src/main/java/com/drawboard/canvas/tools/ImageTool.java`

**Features**:
- **Text Tool**: Click on canvas to create new text box, start typing immediately
- **Image Tool**: Click to open file picker, place image at click location
- Drag-and-drop images from file system onto canvas

### 6.5 Create Toolbar

**Goal**: UI for selecting tools and formatting options.

**Files to Create**:
- `src/main/resources/fxml/Toolbar.fxml`
- `src/main/java/com/drawboard/ui/ToolbarController.java`

**Buttons**:
- Selection tool (cursor icon)
- Text tool (T icon)
- Image tool (picture icon)
- Pen tool (pen icon)
- Highlighter tool (marker icon)
- Color picker
- Stroke width slider
- Rich text formatting (bold, italic, heading, etc.)

**Questions**:
- Should toolbar state (selected tool, color, etc.) be managed by ToolbarController or a separate ToolState service?

---

## Phase 7: Persistence Integration (Auto-Save)

### 7.1 Implement Auto-Save

**Goal**: Automatically save changes to disk after edits.

**File to Create**: `src/main/java/com/drawboard/service/AutoSaveService.java`

**Implementation**:
```java
@Singleton
public class AutoSaveService {
    private static final long DEBOUNCE_MILLIS = 2000; // 2 seconds
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> saveTask;

    public void scheduleSave(Runnable saveAction) {
        if (saveTask != null) {
            saveTask.cancel(false);
        }
        saveTask = scheduler.schedule(saveAction, DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
    }
}
```

**Integration**:
- Call `scheduleSave()` whenever a canvas element is modified
- Save indicator in status bar (e.g., "Saving..." → "All changes saved")

**Questions**:
- Should we save the entire page or just individual elements (delta updates)?
- How to handle save errors (show notification)?

### 7.2 Load Notebooks on Startup

**Goal**: Automatically load all notebooks when app starts.

**File to Edit**: `src/main/java/com/drawboard/ui/MainWindowController.java`

**Implementation**:
```java
@FXML
public void initialize() {
    List<Notebook> notebooks = notebookService.getAllNotebooks();
    populateNavigationTree(notebooks);
}
```

### 7.3 Handle Application Close

**Goal**: Save any pending changes before exiting.

**File to Edit**: `src/main/java/com/drawboard/app/DrawboardApplication.java`

**Implementation**:
```java
@Override
public void stop() {
    log.info("Shutting down Drawboard application");

    // Flush any pending saves
    autoSaveService.flushPendingSaves();

    if (beanScope != null) {
        beanScope.close();
    }
}
```

---

## Phase 8: Polish and UX Improvements

### 8.1 Implement Undo/Redo

**Goal**: Allow users to undo/redo actions.

**File to Create**: `src/main/java/com/drawboard/service/UndoRedoService.java`

**Implementation**:
- Command pattern: each action is a `Command` with `execute()` and `undo()`
- Maintain two stacks: undo stack and redo stack
- Keyboard shortcuts: Ctrl/Cmd+Z (undo), Ctrl/Cmd+Shift+Z (redo)

**Questions**:
- What granularity for commands (every keystroke vs. completed actions)?
- Max undo stack size?

### 8.2 Implement Copy/Paste

**Goal**: Copy and paste canvas elements.

**Features**:
- Ctrl/Cmd+C to copy selected element(s)
- Ctrl/Cmd+V to paste (offset by 10px to avoid overlap)
- Store in clipboard as JSON

### 8.3 Add Keyboard Shortcuts

**Goal**: Common keyboard shortcuts for efficiency.

**Shortcuts**:
- `Ctrl/Cmd+N`: New notebook/chapter/page (context-sensitive)
- `Ctrl/Cmd+S`: Force save
- `Ctrl/Cmd+Z`: Undo
- `Ctrl/Cmd+Shift+Z`: Redo
- `Ctrl/Cmd+C/V/X`: Copy/Paste/Cut
- `Delete`: Delete selected element
- `Esc`: Deselect / Cancel current tool

### 8.4 Add Canvas Navigation

**Goal**: Pan and zoom the canvas.

**Features**:
- Scroll wheel to zoom in/out
- Middle mouse button drag to pan
- Zoom controls in toolbar (zoom in, zoom out, reset to 100%)
- Show current zoom level in status bar

**Questions**:
- Should zoom affect element sizes or just viewport?
- How to handle very large canvases (virtualization)?

### 8.5 Settings and Preferences

**Goal**: User-configurable settings.

**File to Create**: `src/main/java/com/drawboard/service/SettingsService.java`

**Settings**:
- Default notebooks directory
- Auto-save interval
- Default canvas size
- Theme (light/dark mode in future)

**Storage**: Save settings as JSON in `~/.drawboard/settings.json`

---

## Phase 9: Testing

### 9.1 Unit Tests

**Goal**: Test domain models, services, and storage.

**Files to Create**:
- `src/test/java/com/drawboard/domain/NotebookTest.java`
- `src/test/java/com/drawboard/storage/FileStorageServiceTest.java`
- `src/test/java/com/drawboard/service/NotebookServiceTest.java`
- `src/test/java/com/drawboard/service/PageServiceTest.java`

**Test Coverage**:
- JSON serialization/deserialization
- CRUD operations for notebooks, chapters, pages
- File storage operations

### 9.2 Integration Tests

**Goal**: Test end-to-end workflows.

**Files to Create**:
- `src/test/java/com/drawboard/integration/NotebookWorkflowTest.java`

**Test Scenarios**:
- Create notebook → add chapter → add page → add elements → save → load
- Move elements, update text, delete elements
- Auto-save and reload

### 9.3 Manual Testing Checklist

**Goal**: Verify UI behavior and edge cases.

**Test Cases**:
- Create and delete notebooks/chapters/pages
- Add text, images, and drawings to canvas
- Move and resize elements
- Undo/redo actions
- Copy/paste elements
- Save and reload application
- Handle missing files gracefully
- Test on different OS (macOS, Windows, Linux)

---

## Phase 10: Packaging and Distribution

### 10.1 Create Native Runtime Image

**Goal**: Use jlink to create a custom JRE with the app.

**Command**:
```bash
mvn clean package jlink:jlink
```

**Output**: `target/jlink-image/bin/drawboard`

### 10.2 Create Installers (Optional)

**Goal**: Platform-specific installers for easy distribution.

**Tools**:
- **jpackage** (built into JDK): Create `.dmg` (macOS), `.exe` (Windows), `.deb/.rpm` (Linux)

**Command**:
```bash
jpackage --type dmg --input target/jlink-image --name Drawboard --main-jar drawboard.jar
```

### 10.3 Application Icon

**Goal**: Custom icon for the app.

**Files to Create**:
- `src/main/resources/icons/drawboard.png` (or `.ico` for Windows)

**Update**: Configure in `jpackage` command

---

## Future Enhancements (Post-MVP)

### Search Functionality
- Full-text search across all notebooks
- Index with SQLite FTS5 or Apache Lucene
- Search UI in toolbar with results sidebar

### Export Features
- Export page as PDF
- Export page as image (PNG/JPEG)
- Export notebook as ZIP archive

### Advanced Drawing Tools
- Shapes (rectangle, circle, arrow)
- Text annotations on drawings
- Eraser tool (or pen in erase mode)

### Collaboration (Long-term)
- Cloud sync (Dropbox, Google Drive, or custom backend)
- Conflict resolution for concurrent edits
- Shared notebooks

### Android App
- Kotlin rewrite of UI layer
- Reuse JSON storage format
- Sync with desktop app

---

## Open Questions

Please clarify these before implementation:

1. **Storage Structure**:
   - Should we use UUIDs or human-readable names in directory names?
   - Should chapters contain full page objects or just page IDs?

2. **Canvas Rendering**:
   - One WebView per text element or one shared WebView?
   - Should we use WebView or a dedicated rich text library (e.g., RichTextFX)?

3. **Rich Text**:
   - What level of formatting is needed? (headings, bold, italic, colors, fonts?)
   - Should we support lists, tables, links?

4. **Drawing Tools**:
   - Should we smooth/interpolate drawing paths for cleaner lines?
   - Separate eraser tool or integrated into pen?

5. **Service State Management**:
   - Should services maintain current state (open notebook/page) or always require IDs?
   - Where should auto-save debouncing happen (service or UI)?

6. **Undo/Redo**:
   - What granularity for undo commands (keystroke-level or action-level)?
   - Should we persist undo history across sessions?

7. **Multi-instance**:
   - Should we support multiple Drawboard instances running simultaneously?
   - File locking to prevent corruption?

8. **Performance**:
   - How many elements can a page reasonably contain before performance degrades?
   - Should we implement virtualization for very large canvases?

---

## Summary

This plan provides a comprehensive roadmap from scaffolding to a fully functional MVP. Each phase builds on the previous one, allowing for iterative development and testing.

**Estimated Effort**:
- Phase 1-3 (Domain, Storage, Service): 1-2 weeks
- Phase 4 (UI Shell): 1 week
- Phase 5 (Canvas Implementation): 2-3 weeks
- Phase 6 (Interactivity): 2-3 weeks
- Phase 7 (Persistence): 1 week
- Phase 8 (Polish): 1-2 weeks
- Phase 9 (Testing): 1 week
- Phase 10 (Packaging): 1 week

**Total**: ~10-15 weeks for MVP

---

**Document Version**: 1.0
**Last Updated**: 2026-03-10
**Status**: Ready for implementation
