package com.drawboard.ui;

import com.drawboard.domain.Chapter;
import com.drawboard.domain.Notebook;
import com.drawboard.domain.Page;
import com.drawboard.service.NotebookService;
import com.drawboard.service.ObsidianImportService;
import com.drawboard.service.PageService;
import com.drawboard.service.PreferencesService;
import com.drawboard.service.SearchService;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Main window controller for the Drawboard application.
 * Manages the navigation tree and coordinates between UI components.
 */
public class MainWindowController {
    private static final Logger log = LoggerFactory.getLogger(MainWindowController.class);

    @FXML private ComboBox<NotebookItem> notebookSelector;
    @FXML private Accordion chapterAccordion;
    @FXML private StackPane canvasArea;
    @FXML private Label placeholderLabel;
    @FXML private Label statusLabel;
    @FXML private Label pageInfoLabel;
    @FXML private Button btnNewChapter;
    @FXML private Button btnNewPage;
    @FXML private Button btnNewNotebook;
    @FXML private Button btnSearch;
    @FXML private SplitPane splitPane;
    @FXML private ToggleButton btnSelectTool;
    @FXML private ToggleButton btnTextTool;
    @FXML private ToggleButton btnPenTool;
    @FXML private MenuItem menuDeleteNotebook;
    @FXML private HBox toolSettingsContainer;

    private final NotebookService notebookService;
    private final PageService pageService;
    private final PreferencesService preferencesService;
    private final ObsidianImportService obsidianImportService;
    private final SearchService searchService;

    private String currentNotebookId;
    private String currentChapterId;

    private CanvasEditorController canvasEditor;
    private PauseTransition splitPaneSaveDebounce;
    private boolean isCollapsingPanes = false; // Guard against recursive expansion/collapse

    public MainWindowController(NotebookService notebookService, PageService pageService,
                               PreferencesService preferencesService,
                               ObsidianImportService obsidianImportService,
                               SearchService searchService) {
        this.notebookService = notebookService;
        this.pageService = pageService;
        this.preferencesService = preferencesService;
        this.obsidianImportService = obsidianImportService;
        this.searchService = searchService;
    }

    @FXML
    public void initialize() {
        // Initialize canvas editor
        canvasEditor = new CanvasEditorController(pageService, preferencesService, canvasArea);

        setupIcons();
        setupNotebookSelector();
        setupToolButtons();
        setupSplitPane();
        setupKeyboardShortcuts();
        loadNotebooks();

        // Apply saved background color
        applyBackgroundColor(preferencesService.getBackgroundColor());

        // Restore last opened page
        restoreLastOpenedPage();

        updateStatus("Ready");
    }

    private void setupKeyboardShortcuts() {
        // Set up Ctrl+V (Cmd+V on Mac) for paste on canvas area
        // Note: We add an event filter instead of handler to avoid overwriting
        // the SelectionTool's DELETE key handler
        canvasArea.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.isShortcutDown() && event.getCode() == javafx.scene.input.KeyCode.V) {
                handlePaste();
                event.consume();
            }
            // Let other keys pass through to the tool handlers
        });

        // Make canvas area focusable so it can receive keyboard events
        canvasArea.setFocusTraversable(true);
    }

    private void setupIcons() {
        // Set icon for New Notebook button
        javafx.scene.Node notebookIcon = IconLoader.loadIcon("notebook");
        if (notebookIcon != null) {
            btnNewNotebook.setGraphic(notebookIcon);
            btnNewNotebook.setText(""); // Remove text, show only icon
            btnNewNotebook.setTooltip(new Tooltip("New Notebook"));
        }

        // Set icon for New Chapter button
        javafx.scene.Node chapterIcon = IconLoader.loadIcon("chapter");
        if (chapterIcon != null) {
            btnNewChapter.setGraphic(chapterIcon);
            btnNewChapter.setText(""); // Remove text, show only icon
            btnNewChapter.setTooltip(new Tooltip("New Chapter"));
        }

        // Set icon for New Page button
        javafx.scene.Node pageIcon = IconLoader.loadIcon("page");
        if (pageIcon != null) {
            btnNewPage.setGraphic(pageIcon);
            btnNewPage.setText(""); // Remove text, show only icon
            btnNewPage.setTooltip(new Tooltip("New Page"));
        }

        // Set icon for Search button
        javafx.scene.Node searchIcon = IconLoader.loadIcon("search");
        if (searchIcon != null) {
            btnSearch.setGraphic(searchIcon);
            btnSearch.setText(""); // Remove text, show only icon
            btnSearch.setTooltip(new Tooltip("Search (Ctrl+F)"));
        }
    }

    private void setupSplitPane() {
        // Restore divider position
        double savedPosition = preferencesService.getSplitPaneDividerPosition();
        splitPane.setDividerPositions(savedPosition);

        // Create debounce timer (500ms delay)
        splitPaneSaveDebounce = new PauseTransition(Duration.millis(500));

        // Save divider position when changed (debounced)
        splitPane.getDividers().get(0).positionProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // Reset the timer each time position changes
                splitPaneSaveDebounce.stop();
                splitPaneSaveDebounce.setOnFinished(e -> {
                    preferencesService.saveSplitPaneDividerPosition(newVal.doubleValue());
                });
                splitPaneSaveDebounce.playFromStart();
            }
        });
    }

    private void restoreLastOpenedPage() {
        preferencesService.getLastOpenedPage().ifPresent(lastPage -> {
            log.info("Restoring last opened page: {}/{}/{}", lastPage.notebookId(), lastPage.chapterId(), lastPage.pageId());

            // Select the notebook
            for (NotebookItem item : notebookSelector.getItems()) {
                if (item.id().equals(lastPage.notebookId())) {
                    notebookSelector.setValue(item);
                    // Find and select the page in the accordion
                    selectPageInAccordion(lastPage.chapterId(), lastPage.pageId());
                    log.info("Restored last opened page successfully");
                    return;
                }
            }
            log.warn("Last opened page not found");
        });
    }

    private void selectPageInAccordion(String chapterId, String pageId) {
        for (TitledPane pane : chapterAccordion.getPanes()) {
            ChapterPane chapterPane = (ChapterPane) pane.getUserData();
            if (chapterPane.chapterId().equals(chapterId)) {
                chapterAccordion.setExpandedPane(pane);
                // Find and select the page in the ListView
                ListView<PageItem> pageList = (ListView<PageItem>) pane.getContent();
                for (PageItem item : pageList.getItems()) {
                    if (item.id().equals(pageId)) {
                        pageList.getSelectionModel().select(item);
                        return;
                    }
                }
            }
        }
    }

    private void setupToolButtons() {
        // Create a toggle group for tool buttons
        ToggleGroup toolGroup = new ToggleGroup();
        btnSelectTool.setToggleGroup(toolGroup);
        btnTextTool.setToggleGroup(toolGroup);
        btnPenTool.setToggleGroup(toolGroup);

        // Select tool is selected by default
        btnSelectTool.setSelected(true);

        // Set icons for tool buttons
        javafx.scene.Node selectIcon = IconLoader.loadIcon("select");
        if (selectIcon != null) {
            btnSelectTool.setGraphic(selectIcon);
            btnSelectTool.setText(""); // Remove text, show only icon
        }

        javafx.scene.Node textIcon = IconLoader.loadIcon("text");
        if (textIcon != null) {
            btnTextTool.setGraphic(textIcon);
            btnTextTool.setText(""); // Remove text, show only icon
        }

        javafx.scene.Node penIcon = IconLoader.loadIcon("pen");
        if (penIcon != null) {
            btnPenTool.setGraphic(penIcon);
            btnPenTool.setText(""); // Remove text, show only icon
        }

        // Add tooltips to tool buttons
        btnSelectTool.setTooltip(new Tooltip("Select Tool - Select and move elements"));
        btnTextTool.setTooltip(new Tooltip("Text Tool - Click to add text"));
        btnPenTool.setTooltip(new Tooltip("Pen Tool - Draw freehand"));
    }

    private void setupNotebookSelector() {
        // Setup accordion to only allow one expanded pane at a time
        setupAccordion();

        // Setup notebook selector
        notebookSelector.setMaxWidth(Double.MAX_VALUE);
        notebookSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                currentNotebookId = newVal.id();
                loadChaptersForNotebook(newVal.id());
                btnNewChapter.setDisable(false);
                btnNewPage.setDisable(true);
                menuDeleteNotebook.setDisable(false);
                pageInfoLabel.setText("Notebook: " + newVal.name());

                // Try to restore last opened page for this notebook
                preferencesService.getLastOpenedPageForNotebook(newVal.id()).ifPresentOrElse(
                    lastPage -> {
                        if (lastPage.notebookId().equals(newVal.id())) {
                            selectPageInAccordion(lastPage.chapterId(), lastPage.pageId());
                        }
                    },
                    this::clearCanvas
                );
            } else {
                currentNotebookId = null;
                chapterAccordion.getPanes().clear();
                btnNewChapter.setDisable(true);
                btnNewPage.setDisable(true);
                menuDeleteNotebook.setDisable(true);
                pageInfoLabel.setText("");
                clearCanvas();
            }
        });

    }

    private void setupAccordion() {
        // Don't add any accordion-level listener - let individual panes handle it
    }

    private void loadChaptersForNotebook(String notebookId) {
        chapterAccordion.getPanes().clear();

        Notebook notebook = notebookService.getNotebook(notebookId);
        if (notebook == null) {
            return;
        }

        log.info("Loading {} chapters for notebook {}", notebook.chapters().size(), notebookId);

        for (Chapter chapter : notebook.chapters()) {
            TitledPane chapterPane = createChapterPane(notebookId, chapter);
            chapterAccordion.getPanes().add(chapterPane);
            log.debug("Added chapter pane: {} with {} pages", chapter.name(), chapter.pageIds().size());
        }

        log.info("Total panes in accordion: {}", chapterAccordion.getPanes().size());

        // Expand the first pane if there are any chapters
        if (!chapterAccordion.getPanes().isEmpty()) {
            chapterAccordion.setExpandedPane(chapterAccordion.getPanes().get(0));
        }
    }

    private TitledPane createChapterPane(String notebookId, Chapter chapter) {
        TitledPane pane = new TitledPane();
        pane.setText(chapter.name());
        pane.setUserData(new ChapterPane(chapter.id()));
        pane.setAnimated(false); // Disable animation to avoid conflicts
        pane.setExpanded(false); // Start collapsed

        // Listen for expansion and use Accordion's setExpandedPane to manage single expansion
        pane.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
            if (isExpanded && !isCollapsingPanes) {
                // Use the Accordion's setExpandedPane method which handles collapsing others
                isCollapsingPanes = true;
                try {
                    chapterAccordion.setExpandedPane(pane);
                } finally {
                    isCollapsingPanes = false;
                }
            } else if (!isExpanded) {
                // Clear selection when chapter is collapsed to avoid stale selection
                ListView<PageItem> pageList = (ListView<PageItem>) pane.getContent();
                pageList.getSelectionModel().clearSelection();
            }
        });

        // Create ListView for pages
        ListView<PageItem> pageList = new ListView<>();
        pageList.getItems().clear();

        for (String pageId : chapter.pageIds()) {
            Page page = pageService.getPage(notebookId, chapter.id(), pageId);
            if (page != null) {
                pageList.getItems().add(new PageItem(page.id(), page.name()));
            }
        }

        // Handle page selection
        pageList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                currentChapterId = chapter.id();
                btnNewPage.setDisable(false);
                loadPage(notebookId, chapter.id(), newVal);
            }
        });

        // Add context menu to page list
        pageList.setContextMenu(createPageContextMenu(pageList));

        pane.setContent(pageList);

        // Add context menu to chapter pane
        pane.setContextMenu(createChapterContextMenu(chapter.id()));

        return pane;
    }

    private ContextMenu createChapterContextMenu(String chapterId) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem newPage = new MenuItem("New Page");
        newPage.setOnAction(e -> {
            currentChapterId = chapterId;
            handleNewPage();
        });

        MenuItem rename = new MenuItem("Rename Chapter");
        rename.setOnAction(e -> handleRenameChapter(chapterId));

        MenuItem delete = new MenuItem("Delete Chapter");
        delete.setOnAction(e -> handleDeleteChapter(chapterId));

        contextMenu.getItems().addAll(newPage, new SeparatorMenuItem(), rename, delete);
        return contextMenu;
    }

    private ContextMenu createPageContextMenu(ListView<PageItem> pageList) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem rename = new MenuItem("Rename Page");
        rename.setOnAction(e -> {
            PageItem selected = pageList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                handleRenamePage(selected.id());
            }
        });

        MenuItem delete = new MenuItem("Delete Page");
        delete.setOnAction(e -> {
            PageItem selected = pageList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                handleDeletePage(selected.id(), pageList);
            }
        });

        contextMenu.getItems().addAll(rename, delete);
        return contextMenu;
    }

    private void loadNotebooks() {
        notebookSelector.getItems().clear();

        List<Notebook> notebooks = notebookService.getAllNotebooks();

        for (Notebook notebook : notebooks) {
            notebookSelector.getItems().add(new NotebookItem(notebook.id(), notebook.name()));
        }

        updateStatus("Loaded " + notebooks.size() + " notebook(s)");
    }

    @FXML
    private void handleNewNotebook() {
        TextInputDialog dialog = new TextInputDialog("Untitled Notebook");
        dialog.setTitle("New Notebook");
        dialog.setHeaderText("Create a new notebook");
        dialog.setContentText("Notebook name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (!name.isBlank()) {
                Notebook notebook = notebookService.createNotebook(name);
                NotebookItem item = new NotebookItem(notebook.id(), notebook.name());
                notebookSelector.getItems().add(item);
                notebookSelector.setValue(item);
                updateStatus("Created notebook: " + name);
            }
        });
    }

    @FXML
    private void handleDeleteNotebook() {
        if (currentNotebookId == null) {
            showError("Please select a notebook first");
            return;
        }

        NotebookItem currentItem = notebookSelector.getValue();
        if (currentItem == null) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Delete");
        alert.setHeaderText("Delete notebook: " + currentItem.name());
        alert.setContentText("This will permanently delete the notebook and all its chapters and pages.\nThis action cannot be undone. Are you sure?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                notebookService.deleteNotebook(currentNotebookId);

                // Remove from selector
                notebookSelector.getItems().remove(currentItem);
                notebookSelector.setValue(null);

                updateStatus("Deleted notebook: " + currentItem.name());
            } catch (Exception e) {
                log.error("Failed to delete notebook", e);
                showError("Failed to delete notebook: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleNewChapter() {
        if (currentNotebookId == null) {
            showError("Please select a notebook first");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("Untitled Chapter");
        dialog.setTitle("New Chapter");
        dialog.setHeaderText("Create a new chapter");
        dialog.setContentText("Chapter name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (!name.isBlank()) {
                Chapter chapter = notebookService.createChapter(currentNotebookId, name);
                TitledPane chapterPane = createChapterPane(currentNotebookId, chapter);
                chapterAccordion.getPanes().add(chapterPane);
                chapterAccordion.setExpandedPane(chapterPane);
                updateStatus("Created chapter: " + name);
            }
        });
    }

    @FXML
    private void handleNewPage() {
        if (currentNotebookId == null || currentChapterId == null) {
            showError("Please select a chapter first");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("Untitled Page");
        dialog.setTitle("New Page");
        dialog.setHeaderText("Create a new page");
        dialog.setContentText("Page name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (!name.isBlank()) {
                Page page = pageService.createPage(currentNotebookId, currentChapterId, name);

                // Find the current chapter pane and add the page to its list
                for (TitledPane pane : chapterAccordion.getPanes()) {
                    ChapterPane chapterPane = (ChapterPane) pane.getUserData();
                    if (chapterPane.chapterId().equals(currentChapterId)) {
                        ListView<PageItem> pageList = (ListView<PageItem>) pane.getContent();
                        PageItem pageItem = new PageItem(page.id(), page.name());
                        pageList.getItems().add(pageItem);
                        pageList.getSelectionModel().select(pageItem);
                        break;
                    }
                }

                updateStatus("Created page: " + name);
            }
        });
    }

    private void handleRenameChapter(String chapterId) {
        if (currentNotebookId == null) return;

        // Find the chapter pane
        for (TitledPane pane : chapterAccordion.getPanes()) {
            ChapterPane chapterPane = (ChapterPane) pane.getUserData();
            if (chapterPane.chapterId().equals(chapterId)) {
                TextInputDialog dialog = new TextInputDialog(pane.getText());
                dialog.setTitle("Rename Chapter");
                dialog.setHeaderText("Rename chapter");
                dialog.setContentText("New name:");

                Optional<String> result = dialog.showAndWait();
                result.ifPresent(newName -> {
                    if (!newName.isBlank() && !newName.equals(pane.getText())) {
                        try {
                            notebookService.renameChapter(currentNotebookId, chapterId, newName);
                            pane.setText(newName);
                            updateStatus("Renamed chapter to: " + newName);
                        } catch (Exception e) {
                            log.error("Failed to rename chapter", e);
                            showError("Failed to rename chapter: " + e.getMessage());
                        }
                    }
                });
                break;
            }
        }
    }

    private void handleDeleteChapter(String chapterId) {
        if (currentNotebookId == null) return;

        // Find the chapter pane
        for (TitledPane pane : chapterAccordion.getPanes()) {
            ChapterPane chapterPane = (ChapterPane) pane.getUserData();
            if (chapterPane.chapterId().equals(chapterId)) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Confirm Delete");
                alert.setHeaderText("Delete chapter: " + pane.getText());
                alert.setContentText("This action cannot be undone. Are you sure?");

                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    try {
                        notebookService.deleteChapter(currentNotebookId, chapterId);
                        chapterAccordion.getPanes().remove(pane);
                        updateStatus("Deleted chapter: " + pane.getText());
                        clearCanvas();
                    } catch (Exception e) {
                        log.error("Failed to delete chapter", e);
                        showError("Failed to delete chapter: " + e.getMessage());
                    }
                }
                break;
            }
        }
    }

    private void handleRenamePage(String pageId) {
        if (currentNotebookId == null || currentChapterId == null) return;

        // Find the page in the current chapter
        for (TitledPane pane : chapterAccordion.getPanes()) {
            ChapterPane chapterPane = (ChapterPane) pane.getUserData();
            if (chapterPane.chapterId().equals(currentChapterId)) {
                ListView<PageItem> pageList = (ListView<PageItem>) pane.getContent();
                for (PageItem item : pageList.getItems()) {
                    if (item.id().equals(pageId)) {
                        TextInputDialog dialog = new TextInputDialog(item.name());
                        dialog.setTitle("Rename Page");
                        dialog.setHeaderText("Rename page");
                        dialog.setContentText("New name:");

                        Optional<String> result = dialog.showAndWait();
                        result.ifPresent(newName -> {
                            if (!newName.isBlank() && !newName.equals(item.name())) {
                                try {
                                    pageService.renamePage(currentNotebookId, currentChapterId, pageId, newName);
                                    int index = pageList.getItems().indexOf(item);
                                    pageList.getItems().set(index, new PageItem(pageId, newName));
                                    updateStatus("Renamed page to: " + newName);
                                } catch (Exception e) {
                                    log.error("Failed to rename page", e);
                                    showError("Failed to rename page: " + e.getMessage());
                                }
                            }
                        });
                        return;
                    }
                }
            }
        }
    }

    private void handleDeletePage(String pageId, ListView<PageItem> pageList) {
        if (currentNotebookId == null || currentChapterId == null) return;

        PageItem item = pageList.getItems().stream()
            .filter(p -> p.id().equals(pageId))
            .findFirst()
            .orElse(null);

        if (item == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Delete");
        alert.setHeaderText("Delete page: " + item.name());
        alert.setContentText("This action cannot be undone. Are you sure?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                pageService.deletePage(currentNotebookId, currentChapterId, pageId);
                pageList.getItems().remove(item);
                updateStatus("Deleted page: " + item.name());
                clearCanvas();
            } catch (Exception e) {
                log.error("Failed to delete page", e);
                showError("Failed to delete page: " + e.getMessage());
            }
        }
    }

    private void loadPage(String notebookId, String chapterId, PageItem pageItem) {
        Page page = pageService.getPage(notebookId, chapterId, pageItem.id());
        if (page != null) {
            pageInfoLabel.setText("Page: " + page.name() + " (" + page.elements().size() + " elements)");

            // Load page into canvas editor (it will handle clearing)
            canvasEditor.loadPage(notebookId, chapterId, pageItem.id());

            // Hide placeholder when page is loaded
            placeholderLabel.setVisible(false);
        }
    }

    private void clearCanvas() {
        if (canvasEditor != null) {
            canvasEditor.clear();
            // Show placeholder when canvas is cleared
            placeholderLabel.setVisible(true);
        }
    }

    private void applyBackgroundColor(String backgroundColor) {
        // Apply to canvas area
        canvasArea.setStyle("-fx-background-color: " + backgroundColor + ";");

        // Notify canvas editor to update text element backgrounds
        if (canvasEditor != null) {
            canvasEditor.setBackgroundColor(backgroundColor);
        }
    }

    @FXML
    private void handleExit() {
        javafx.application.Platform.exit();
    }

    @FXML
    private void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About Drawboard");
        alert.setHeaderText("Drawboard 0.1.0");
        alert.setContentText("A notebook application with free-form canvas pages.\n\n" +
                           "Built with Java 25 and JavaFX 23");
        alert.showAndWait();
    }

    @FXML
    private void handlePreferences() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PreferencesDialog.fxml"));

            // Create controller with dependencies
            PreferencesDialogController controller = new PreferencesDialogController(preferencesService);
            loader.setController(controller);

            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Preferences");
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialogStage.initOwner(statusLabel.getScene().getWindow());
            dialogStage.setScene(new javafx.scene.Scene(root));
            dialogStage.setResizable(false);

            controller.setDialogStage(dialogStage);
            controller.setOnApply(backgroundColor -> {
                // Apply the new background color to the canvas
                applyBackgroundColor(backgroundColor);
            });

            dialogStage.showAndWait();
        } catch (Exception e) {
            log.error("Failed to open preferences dialog", e);
            showError("Failed to open preferences: " + e.getMessage());
        }
    }

    @FXML
    private void handlePaste() {
        if (canvasEditor != null && canvasEditor.hasPageLoaded()) {
            canvasEditor.handlePaste();
        } else {
            updateStatus("No page loaded - cannot paste");
        }
    }

    @FXML
    private void handleSearch() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SearchDialog.fxml"));

            // Create controller with dependencies
            SearchDialogController controller = new SearchDialogController(searchService);
            loader.setController(controller);

            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Search");
            dialogStage.initModality(javafx.stage.Modality.NONE); // Non-modal so user can keep it open
            dialogStage.initOwner(statusLabel.getScene().getWindow());
            dialogStage.setScene(new javafx.scene.Scene(root));

            controller.setDialogStage(dialogStage);
            controller.setCurrentNotebookId(currentNotebookId);
            controller.setOnResultSelected(result -> {
                // Navigate to the search result
                navigateToSearchResult(result);
            });

            dialogStage.show();
            controller.focusSearchField();
        } catch (Exception e) {
            log.error("Failed to open search dialog", e);
            showError("Failed to open search: " + e.getMessage());
        }
    }

    private void navigateToSearchResult(com.drawboard.domain.search.SearchResult result) {
        // Select the notebook
        for (NotebookItem item : notebookSelector.getItems()) {
            if (item.id().equals(result.notebookId())) {
                // Only change notebook if different
                if (notebookSelector.getValue() == null ||
                    !notebookSelector.getValue().id().equals(result.notebookId())) {
                    notebookSelector.setValue(item);
                }
                break;
            }
        }

        // Navigate to the page
        if (result.pageId() != null) {
            selectPageInAccordion(result.chapterId(), result.pageId());
            updateStatus("Navigated to: " + result.notebookName() + " › " +
                        result.chapterName() + " › " + result.pageName());
        } else if (result.chapterId() != null) {
            // Just expand the chapter if it's a chapter name match
            for (TitledPane pane : chapterAccordion.getPanes()) {
                ChapterPane chapterPane = (ChapterPane) pane.getUserData();
                if (chapterPane.chapterId().equals(result.chapterId())) {
                    chapterAccordion.setExpandedPane(pane);
                    updateStatus("Navigated to: " + result.notebookName() + " › " + result.chapterName());
                    break;
                }
            }
        }
    }

    @FXML
    private void handleImportObsidianVault() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Obsidian Vault Directory");

        // Try to set initial directory to user's Documents folder
        File documentsDir = new File(System.getProperty("user.home"), "Documents");
        if (documentsDir.exists()) {
            directoryChooser.setInitialDirectory(documentsDir);
        }

        File selectedDirectory = directoryChooser.showDialog(notebookSelector.getScene().getWindow());

        if (selectedDirectory != null) {
            importObsidianVault(selectedDirectory.toPath());
        }
    }

    private void importObsidianVault(Path vaultPath) {
        updateStatus("Importing Obsidian vault...");

        try {
            // Import vault (use vault directory name as notebook name)
            Notebook notebook = obsidianImportService.importVault(vaultPath, null);

            // Reload notebook list to include the new notebook
            loadNotebooks();

            // Select the newly imported notebook
            NotebookItem newItem = null;
            for (NotebookItem item : notebookSelector.getItems()) {
                if (item.id().equals(notebook.id())) {
                    newItem = item;
                    break;
                }
            }

            if (newItem != null) {
                // Force reload by setting to null first if it's already selected
                if (notebookSelector.getValue() != null &&
                    notebookSelector.getValue().id().equals(notebook.id())) {
                    notebookSelector.setValue(null);
                }
                notebookSelector.setValue(newItem);
            }

            // Show success message
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Import Successful");
            alert.setHeaderText("Obsidian vault imported");
            alert.setContentText(String.format(
                "Successfully imported vault '%s' as notebook with %d chapters.",
                notebook.name(), notebook.chapters().size()));
            alert.showAndWait();

            updateStatus("Import complete: " + notebook.name());
        } catch (Exception e) {
            log.error("Failed to import Obsidian vault", e);
            showError("Failed to import vault: " + e.getMessage());
            updateStatus("Import failed");
        }
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void handleSelectTool() {
        if (canvasEditor != null) {
            canvasEditor.setActiveTool("Selection");
            updateToolSettings("Selection");
            updateStatus("Selection tool active");
        }
    }

    @FXML
    private void handleTextTool() {
        if (canvasEditor != null) {
            canvasEditor.setActiveTool("Text");
            updateToolSettings("Text");
            updateStatus("Text tool active - Click to add text");
        }
    }

    @FXML
    private void handlePenTool() {
        if (canvasEditor != null) {
            canvasEditor.setActiveTool("Pen");
            updateToolSettings("Pen");
            updateStatus("Pen tool active - Draw on the canvas");
        }
    }

    private void updateToolSettings(String toolName) {
        if (toolSettingsContainer == null) {
            return;
        }

        // Clear existing settings
        toolSettingsContainer.getChildren().clear();

        // Get settings from canvas editor's active tool
        if (canvasEditor != null) {
            canvasEditor.getCurrentToolSettings().ifPresent(settingsNodes -> {
                toolSettingsContainer.getChildren().addAll(settingsNodes);
            });
        }
    }

    // Data classes for UI items
    public record NotebookItem(String id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    public record ChapterPane(String chapterId) {}

    public record PageItem(String id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
