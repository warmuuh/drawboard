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

- **Java 25**: Liberica JDK Full (includes JavaFX)
- **JavaFX 23**: UI framework and canvas rendering
- **fxsvgimage**: SVG support for JavaFX
- **Avaje Inject**: Lightweight, fast dependency injection
- **Avaje JsonB**: JSON serialization for storage
- **Maven**: Build tool
- **jpackage**: Native installer creation (DMG for macOS)

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
- **SDKMAN** installed for Java version management
- **Liberica JDK 25 Full** (includes JavaFX) - managed via SDKMAN
- **Maven** 3.9+

### Setup Environment

```bash
# Activate Java environment (automatically via .sdkmanrc)
sdk env

# Or manually install Liberica JDK 25 with JavaFX if not installed
sdk install java 25.0.2.fx-librca
```

### Development

**Build**
```bash
mvn clean compile
```

**Run Tests**
```bash
mvn test
```

**Run Application**
```bash
mvn javafx:run
```

### Packaging

**Create Native DMG Installer (macOS)**
```bash
./package.sh
```

The DMG installer will be created at `target/dist/Drawboard-1.0.dmg`.

**Manual Packaging**

If you need to customize the packaging:

```bash
# Build JAR and copy dependencies
mvn clean package -DskipTests

# Run jpackage manually
jpackage \
  --type dmg \
  --name Drawboard \
  --app-version 1.0 \
  --vendor "Drawboard" \
  --dest target/dist \
  --input target \
  --main-jar drawboard-0.1.0-SNAPSHOT.jar \
  --main-class com.drawboard.app.DrawboardApplication \
  --java-options "-Dfile.encoding=UTF-8"
```

## Features

The application includes:
- Hierarchical organization with notebooks, chapters, and pages
- Rich text editing with markdown support
- Canvas-based free-form layout
- Drawing tools (pen, highlighter, arrow, line, rectangle, circle)
- Selection tools (intersects and contains modes)
- Image insertion and positioning
- Search functionality
- Clipboard support (paste images and text)
- File-based storage with JSON and automatic saving

## License

TBD
