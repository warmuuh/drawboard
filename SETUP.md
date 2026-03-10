# Drawboard Setup Guide

## Project Status

✅ **Project scaffolding complete!**

The Drawboard project has been successfully initialized with:

- Maven project structure (Java 25)
- JavaFX 23 dependencies
- Avaje Inject (DI) and Avaje JsonB (JSON serialization)
- Module system configured (`module-info.java`)
- Logging with SLF4J and Logback
- Basic application entry point (`DrawboardApplication.java`)
- Architecture documentation (CLAUDE.md, README.md)

## Project Structure

```
drawboard/
├── pom.xml                           # Maven configuration
├── README.md                         # Project overview and architecture
├── CLAUDE.md                         # Development guidelines for AI assistants
├── SETUP.md                          # This file
├── .gitignore                        # Git ignore rules
└── src/
    ├── main/
    │   ├── java/
    │   │   ├── module-info.java      # Java module descriptor
    │   │   └── com/drawboard/
    │   │       ├── app/              # Application entry point
    │   │       │   └── DrawboardApplication.java
    │   │       ├── domain/           # Domain models (ready for implementation)
    │   │       │   └── elements/
    │   │       ├── storage/          # File I/O and persistence
    │   │       │   └── serialization/
    │   │       ├── canvas/           # Canvas rendering and tools
    │   │       │   ├── renderers/
    │   │       │   └── tools/
    │   │       ├── ui/               # JavaFX controllers and views
    │   │       └── service/          # Business logic
    │   └── resources/
    │       ├── logback.xml           # Logging configuration
    │       ├── fxml/                 # JavaFX FXML files
    │       ├── css/                  # Stylesheets
    │       └── icons/                # Application icons
    └── test/
        └── java/
            └── com/drawboard/
                ├── domain/
                ├── storage/
                └── service/
```

## Quick Start

### Prerequisites

1. **Java 25** (via SDKMAN recommended):
   ```bash
   sdk install java 25.0.2-tem
   sdk use java 25.0.2-tem
   ```

2. **Maven 3.9+** (via SDKMAN recommended):
   ```bash
   sdk install maven
   ```

### Build and Run

```bash
# Compile the project
source ~/.sdkman/bin/sdkman-init.sh && mvn clean compile

# Run the application
source ~/.sdkman/bin/sdkman-init.sh && mvn javafx:run

# Run tests
source ~/.sdkman/bin/sdkman-init.sh && mvn test

# Package the application
source ~/.sdkman/bin/sdkman-init.sh && mvn package
```

### Create Native Runtime Image (Optional)

```bash
# Create a jlink image with custom JRE
source ~/.sdkman/bin/sdkman-init.sh && mvn jlink:jlink

# Run from the native image
./target/jlink-image/bin/drawboard
```

## Compilation Status

✅ Project compiles successfully with no errors!

**Note**: Some warnings from Maven/JavaFX libraries are expected (related to Java 25's deprecation warnings for unsafe operations). These don't affect the build.

## Next Steps

The project is ready for feature implementation. The recommended implementation order is:

1. **Domain Models** (`domain/`)
   - Create `Notebook`, `Chapter`, `Page` classes
   - Define canvas element types (sealed interface)
   - Create `TextElement`, `ImageElement`, `DrawingElement` records

2. **Storage Layer** (`storage/`)
   - Implement `FileStorageService` for reading/writing notebooks
   - Add Avaje JsonB serialization for domain models
   - Create file structure management

3. **Service Layer** (`service/`)
   - `NotebookService` - CRUD operations for notebooks
   - `PageService` - Page management and element operations
   - Integrate with storage layer

4. **UI Shell** (`ui/`)
   - Create main window with navigation tree (left) and canvas editor (right)
   - FXML layouts for basic structure
   - Wire up with Avaje DI

5. **Canvas Implementation** (`canvas/`)
   - Hybrid canvas approach (JavaFX Canvas + Nodes)
   - Rich text editing with WebView
   - Image rendering with ImageView
   - Freehand drawing on Canvas

6. **Interactivity** (`canvas/tools/`)
   - Selection tool
   - Move and resize elements
   - Drawing tools (pen, highlighter, shapes)
   - Toolbar for tool selection

7. **Persistence Integration**
   - Auto-save with debouncing
   - Load notebooks on startup
   - Save state on application close

## Development Tips

- **Use SDKMAN**: Always source the SDKMAN init script before running Maven commands
- **Hot reload**: Use `mvn javafx:run` during development for quick iterations
- **Logging**: Check `src/main/resources/logback.xml` to adjust log levels
- **Avaje annotations**: Remember to rebuild after adding `@Singleton`, `@Inject`, or `@Json` annotations

## Useful Maven Commands

```bash
# Clean and compile
mvn clean compile

# Run with JavaFX plugin
mvn javafx:run

# Run tests
mvn test

# Package as JAR
mvn package

# Show dependency tree
mvn dependency:tree

# Update dependencies
mvn versions:display-dependency-updates
```

## Troubleshooting

### JAVA_HOME not set
```bash
source ~/.sdkman/bin/sdkman-init.sh
```

### Module errors
If you get module-related errors, check that `module-info.java` exports/opens only existing packages with content.

### JavaFX not loading
Ensure you're running with the JavaFX Maven plugin: `mvn javafx:run`

---

**Last Updated**: 2026-03-10
**Status**: ✅ Ready for development
