# Claude.md - Drawboard Project

This file contains conventions, architecture decisions, and guidelines for AI assistants (Claude) working on the Drawboard project.

## Project Overview

Drawboard is a desktop notebook application with free-form canvas pages. Users organize content in a hierarchy: **Notebooks → Chapters → Pages**. Each page is a canvas supporting rich text, images, and freehand drawings positioned anywhere.

## Technology Stack

- **Java 25**: Use modern Java features (records, pattern matching, text blocks, etc.)
  - Managed via SDKMAN (`.sdkmanrc` file in project root)
  - Run `sdk env` to activate the correct Java version
- **JavaFX 23**: UI framework
- **fxsvgimage** (`org.girod.javafx.svgimage`): SVG support for JavaFX
  - Load SVG files as JavaFX Nodes: `SVGImage img = SVGLoader.load(<SVG file>)` (returns a Group)
  - Convert JavaFX Nodes to SVG: use `SVGConverter` with `ConverterParameters`
  - Supports: clip paths, gradients, filters, shapes, paths, images, text, use/symbol, transformations, animations (partial)
  - Use for icons, vector graphics, and potentially for exporting canvas content to SVG
- **Avaje Inject**: DI framework (compile-time, annotation-based)
- **Avaje JsonB**: JSON serialization/deserialization
- **Maven**: Build tool
- **jlink**: For creating native runtime images

## Architecture Principles

### Layered Architecture

```
app → ui → service → storage
          ↓         ↓
        domain ← canvas
```

- **domain**: Pure domain models, no dependencies on frameworks
- **storage**: File I/O, JSON serialization, persistence logic
- **service**: Business logic, orchestrates storage and domain
- **canvas**: Canvas rendering logic, element management, drawing tools
- **ui**: JavaFX views and controllers
- **app**: Entry point, DI setup, application lifecycle

### Dependency Rules

1. **Domain models** are annotated with `@Json` for Avaje JsonB serialization, but otherwise framework-agnostic (no JavaFX annotations)
2. **Services** are injected via Avaje `@Singleton` and `@Inject`
3. **Controllers** can depend on services, not storage directly
4. **Storage** layer is the only layer that reads/writes files

### Canvas Hybrid Approach

- **JavaFX Canvas**: For freehand drawing (strokes, paths)
- **JavaFX Nodes**: For text (rich text via WebView or custom component) and images (ImageView)
- **Layout**: Use a `Pane` or `Group` to layer Canvas + Nodes
- **Rich Text**: Consider `WebView` with contentEditable HTML or a custom rich text component

## Code Conventions

### Java Style

- **Package structure**: `com.drawboard.<layer>.<feature>`
- **Naming**:
  - Domain models: `Notebook`, `Page`, `TextElement`
  - Services: `NotebookService`, `PageService`
  - Controllers: `MainWindowController`, `CanvasEditorController`
  - Storage: `FileStorageService`, `JsonPageSerializer`
- **Records** for immutable DTOs and value objects
- **Sealed types** where appropriate (e.g., `sealed interface CanvasElement permits TextElement, ImageElement, DrawingElement`)
- **Text blocks** for multi-line strings (JSON templates, HTML, etc.)

### Dependency Injection (Avaje)

- Mark services with `@Singleton`
- Use constructor injection with `@Inject` (implicit if single constructor)
- Controllers can be singletons or instantiated manually (JavaFX limitation)

Example:
```java
package com.drawboard.service;

import io.avaje.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class NotebookService {
    private final FileStorageService storage;

    @Inject
    public NotebookService(FileStorageService storage) {
        this.storage = storage;
    }
}
```

### JSON Serialization (Avaje JsonB)

- Annotate domain models with `@Json` for serialization
- Use `@Json.Property` for custom field names if needed
- DTOs for serialization can be records
- **Polymorphic types**: Use `@Json.SubType` to specify subtypes for sealed interfaces/abstract classes
  - Default type discriminator is `@type` in JSON
  - Each subtype needs a name for the discriminator value
- **module-info.java requirements**:
  - Must `require io.avaje.jsonb.plugin`
  - Must `open` packages to `io.avaje.jsonb` for reflection
  - Must `provide io.avaje.jsonb.spi.JsonbExtension` with generated component

Example (polymorphic sealed interface):
```java
@Json
@Json.SubType(type = TextElement.class, name = "TEXT")
@Json.SubType(type = ImageElement.class, name = "IMAGE")
@Json.SubType(type = DrawingElement.class, name = "DRAWING")
public sealed interface CanvasElement
    permits TextElement, ImageElement, DrawingElement {
    String id();
    double x();
    double y();
    int zIndex();
}
```

Example (simple record):
```java
package com.drawboard.domain;

import io.avaje.jsonb.Json;

@Json
public record Page(
    String pageId,
    List<CanvasElement> elements
) {}
```

module-info.java snippet:
```java
requires io.avaje.jsonb;
requires io.avaje.jsonb.plugin;

opens com.drawboard.domain to io.avaje.jsonb;

provides io.avaje.jsonb.spi.JsonbExtension
    with com.drawboard.domain.jsonb.GeneratedJsonComponent;
```

### File Structure

- Each page is stored in its own directory: `<notebook>/<chapter>/<page-id>/`
- Files:
  - `page.json`: Page metadata and element definitions
  - Media files: `image-1.png`, `image-2.jpg`, etc.

### Testing

- Use JUnit 5 for unit tests
- Test services independently with mock storage
- Integration tests for storage layer (read/write files)
- UI tests can be added later with TestFX

## Implementation Workflow

1. **Start with domain models**: Define `Notebook`, `Chapter`, `Page`, `CanvasElement` and subtypes
2. **Storage layer**: Implement file-based persistence with Avaje JsonB
3. **Service layer**: Business logic for creating, loading, saving notebooks
4. **UI shell**: Navigation tree and empty canvas area
5. **Canvas implementation**: Start with rich text, then images, then drawings
6. **Interactivity**: Selection, move, resize, drawing tools
7. **Persistence integration**: Auto-save on changes

## Key Design Decisions

### Canvas Element Types

Sealed interface for type safety:
```java
sealed interface CanvasElement permits TextElement, ImageElement, DrawingElement {
    String id();
    double x();
    double y();
    int zIndex();
}
```

### Rich Text Strategy

**Options**:
1. **WebView with contentEditable**: Easy rich text, but heavier
2. **Custom RichTextFX**: Third-party library, more control
3. **Styled TextFlow**: JavaFX built-in, limited formatting

**Decision**: Start with **WebView** for MVP (simple, full HTML/CSS support), consider RichTextFX later for performance.

### Auto-Save Strategy

- Debounce save operations (e.g., 2 seconds after last edit)
- Use `java.nio.file.WatchService` to detect external changes (optional)
- Show save indicator in UI

## Common Tasks

### Adding a New Canvas Element Type

1. Define the element type in `domain/elements/`
2. Add serialization support in `storage/serialization/`
3. Implement rendering in `canvas/renderers/`
4. Add tool/interaction logic in `canvas/tools/`
5. Update UI toolbar if needed

### Adding a New Service

1. Create service class in `service/` package
2. Annotate with `@Singleton`
3. Inject dependencies via constructor
4. Write unit tests

### Adding a New UI View

1. Create FXML file in `src/main/resources/fxml/`
2. Create controller in `ui/` package
3. Load FXML in `MainWindowController` or app entry point
4. Wire up services via manual injection or lookup

## Logging

- Use SLF4J with Logback
- Log levels:
  - **TRACE**: Very detailed diagnostic information (use sparingly)
  - **DEBUG**: Detailed flow, helpful during development
  - **INFO**: Key operations (file saved, notebook loaded) - avoid obvious messages like "Starting UI"
  - **WARN**: Recoverable issues (file not found, parse error)
  - **ERROR**: Unrecoverable errors
- **Style**: Don't log obvious startup messages at INFO/DEBUG - use TRACE if at all

Example:
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotebookService {
    private static final Logger log = LoggerFactory.getLogger(NotebookService.class);

    public void saveNotebook(Notebook notebook) {
        log.info("Saving notebook: {}", notebook.name());
        // ...
    }
}
```

## Future Considerations

- **Search**: Index pages with SQLite FTS5 or Apache Lucene
- **Export**: PDF generation with Apache PDFBox or iText
- **Sync**: Design storage format to support conflict resolution (CRDTs or operational transforms)
- **Android**: Kotlin rewrite of UI layer, shared JSON format

## Build and Run

### Prerequisites
- SDKMAN installed for Java version management
- Maven 3.9+ installed

### Setup
```bash
# Activate Java 25 environment
sdk env

# Compile project
mvn clean compile

# Run application
mvn javafx:run
```

### Module System Notes
- Project uses Java Platform Module System (JPMS)
- All domain and UI packages must be exported/opened in `module-info.java`
- Avaje JsonB requires specific module configuration (see JSON Serialization section)

## Questions/Decisions Log

- **Q**: Should we support collaborative editing?
  - **A**: Not in scope for MVP, but design storage format to allow future sync

- **Q**: How to handle large images?
  - **A**: Store full-size, render with downscaling in JavaFX. Add image optimization later if needed.

- **Q**: Undo/redo strategy?
  - **A**: Command pattern with a stack of operations. Serialize commands for recovery.

- **Q**: Should Chapter contain full Page objects or just page IDs?
  - **A**: Just page IDs (`List<String> pageIds`) - aligns with file-based storage where each page is in its own directory

---

**Last Updated**: 2026-03-10
