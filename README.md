# Drawboard

A desktop notebook application with free-form canvas pages, inspired by OneNote. Organize your notes, images, and drawings in a hierarchical structure of notebooks, chapters, and pages.

## Project Scope

### Core Features

**Organizational Structure**
- **Notebooks**: Top-level containers for related content
- **Chapters**: Organize pages within a notebook
- **Pages**: Free-form canvas where content lives

**Canvas Elements**
- **Rich Text**: Formatted text with headings, bold, italic, etc.
- **Images**: Add and position images anywhere on the canvas
- **Freehand Drawings**: Annotate with arrows, highlights, and sketches
- **Free Positioning**: Place any element anywhere with drag-and-drop

**Storage**
- File-based, human-readable format (JSON)
- Each page is a directory containing its media and structure
- Auto-save with debouncing
- Future: Full-text search with indexing

### Platform
- **Desktop only** (Windows, macOS, Linux via JavaFX)
- Future: Android app (data format compatibility)
- **Not planned**: Web app, hosted SaaS

## Technology Stack

- **Java 25**: Latest LTS features
- **JavaFX 23**: UI framework and canvas rendering
- **Avaje Inject**: Lightweight, fast dependency injection
- **Avaje JsonB**: JSON serialization for storage
- **Maven**: Build tool
- **jlink**: Native runtime image creation

## Architecture

### Layered Structure

```
com.drawboard
├── app/                    # Application entry point, DI bootstrap
├── domain/                 # Core domain models
│   ├── Notebook
│   ├── Chapter
│   ├── Page
│   └── elements/           # Canvas elements (Text, Image, Drawing)
├── storage/                # File I/O and persistence
│   ├── FileStorageService
│   └── serialization/      # JSON mapping
├── canvas/                 # Canvas rendering and interaction
│   ├── CanvasManager
│   ├── ElementRenderer
│   └── tools/              # Drawing tools, selection, etc.
├── ui/                     # JavaFX UI components
│   ├── MainWindow
│   ├── NavigationTree      # Notebook/Chapter/Page tree
│   ├── CanvasEditor        # Main canvas editing area
│   └── Toolbar             # Tools and formatting
└── service/                # Business logic layer
    ├── NotebookService
    ├── PageService
    └── SearchService (future)
```

### Canvas Rendering Strategy

**Hybrid Approach**:
- **JavaFX Canvas** for freehand drawings (paths, strokes)
- **JavaFX Nodes** (TextArea with rich formatting, ImageView) layered on a Pane for text and images
- **Benefits**: Leverage built-in text editing, simpler image handling, full control over drawings

### Storage Format

```
~/Documents/DrawboardNotebooks/
├── Work/                          (Notebook)
│   ├── notebook.json              (metadata: name, created, modified)
│   ├── Chapter-1-Meetings/        (Chapter)
│   │   ├── chapter.json           (metadata)
│   │   ├── page-001/              (Page)
│   │   │   ├── page.json          (elements, positions, styling)
│   │   │   ├── image-1.png
│   │   │   └── image-2.jpg
│   │   └── page-002/
│   └── Chapter-2-Notes/
└── Personal/
```

**page.json structure**:
```json
{
  "pageId": "uuid",
  "created": "2026-03-10T10:00:00Z",
  "modified": "2026-03-10T11:30:00Z",
  "elements": [
    {
      "id": "uuid",
      "type": "richtext",
      "x": 100,
      "y": 50,
      "width": 400,
      "height": 200,
      "content": "<html>...",  // Rich text HTML
      "zIndex": 1
    },
    {
      "id": "uuid",
      "type": "image",
      "x": 500,
      "y": 100,
      "width": 300,
      "height": 200,
      "filename": "image-1.png",
      "zIndex": 2
    },
    {
      "id": "uuid",
      "type": "drawing",
      "x": 200,
      "y": 300,
      "paths": [
        {"color": "#FF0000", "width": 2, "points": [[x1,y1], [x2,y2], ...]}
      ],
      "zIndex": 3
    }
  ]
}
```

## Building and Running

### Prerequisites
- SDKMAN installed for Java version management
- Java 25 (managed via SDKMAN)
- Maven 3.9+

### Setup Environment

```bash
# Activate Java 25 environment (automatically via .sdkmanrc)
sdk env

# Or manually install Java 25 if not installed
sdk install java 25-tem
```

### Build
```bash
mvn clean compile
```

### Run Tests
```bash
mvn test
```

### Run
```bash
mvn javafx:run
```

### Create Native Runtime Image
```bash
mvn jlink:jlink
```

The runtime image will be in `target/jlink-image/`.

### Run from Runtime Image
```bash
./target/jlink-image/bin/drawboard
```

## Development Roadmap

### Phase 1: Domain Models ✅ COMPLETE
- [x] Project scaffolding
- [x] Domain models (Notebook, Chapter, Page, Canvas Elements)
- [x] JSON serialization with Avaje JsonB
- [x] Sealed interface for polymorphic canvas elements

### Phase 2: Storage Layer ✅ COMPLETE
- [x] File-based storage service
- [x] Directory structure for notebooks/chapters/pages
- [x] Image storage and retrieval
- [x] JSON persistence with proper error handling

### Phase 3: Service Layer ✅ COMPLETE
- [x] NotebookService (CRUD operations)
- [x] PageService (CRUD operations + element management)
- [x] Business logic layer with validation
- [x] Comprehensive test coverage (28 tests passing)

### Phase 4: UI Shell ✅ COMPLETE
- [x] Main window with menu bar and toolbar
- [x] Navigation tree showing notebook hierarchy
- [x] Create, rename, delete notebooks/chapters/pages
- [x] Context menus for all operations
- [x] Status bar and page info display
- [x] DI integration with Avaje Inject

### Phase 5: Canvas Implementation (Next)
- [ ] Canvas rendering architecture
- [ ] Rich text element rendering (WebView)
- [ ] Image element rendering (ImageView)
- [ ] Drawing element rendering (JavaFX Canvas)
- [ ] Canvas manager coordinating all renderers

### Phase 6: Interactivity
- [ ] Selection tool
- [ ] Move and resize elements
- [ ] Drawing tools (pen, highlighter)
- [ ] Text and image insertion
- [ ] Toolbar for tool selection

### Phase 2: Polish
- [ ] Undo/redo
- [ ] Copy/paste elements
- [ ] Multiple notebooks management
- [ ] Search functionality
- [ ] Keyboard shortcuts
- [ ] Settings/preferences

### Phase 3: Advanced
- [ ] Full-text search with indexing (SQLite FTS?)
- [ ] Export pages (PDF, images)
- [ ] Themes/styling
- [ ] Plugin system

### Phase 4: Mobile
- [ ] Android app with data sync

## License

TBD
