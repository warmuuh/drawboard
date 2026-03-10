package com.drawboard.ui;

import com.drawboard.domain.Chapter;
import com.drawboard.domain.Notebook;
import com.drawboard.domain.Page;
import com.drawboard.service.NotebookService;
import com.drawboard.service.PageService;
import com.drawboard.service.PreferencesService;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    @FXML private Label statusLabel;
    @FXML private Label pageInfoLabel;
    @FXML private Button btnNewChapter;
    @FXML private Button btnNewPage;
    @FXML private SplitPane splitPane;
    @FXML private ToggleButton btnSelectTool;
    @FXML private ToggleButton btnTextTool;
    @FXML private ToggleButton btnPenTool;

    private final NotebookService notebookService;
    private final PageService pageService;
    private final PreferencesService preferencesService;

    private String currentNotebookId;
    private String currentChapterId;

    private CanvasEditorController canvasEditor;
    private PauseTransition splitPaneSaveDebounce;

    public MainWindowController(NotebookService notebookService, PageService pageService, PreferencesService preferencesService) {
        this.notebookService = notebookService;
        this.pageService = pageService;
        this.preferencesService = preferencesService;
    }

    @FXML
    public void initialize() {
        // Initialize canvas editor
        canvasEditor = new CanvasEditorController(pageService, preferencesService, canvasArea);

        setupNotebookSelector();
        setupToolButtons();
        setupSplitPane();
        loadNotebooks();

        // Restore last opened page
        restoreLastOpenedPage();

        updateStatus("Ready");
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
    }

    private void setupNotebookSelector() {
        // Setup notebook selector
        notebookSelector.setMaxWidth(Double.MAX_VALUE);
        notebookSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                currentNotebookId = newVal.id();
                loadChaptersForNotebook(newVal.id());
                btnNewChapter.setDisable(false);
                btnNewPage.setDisable(true);
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
                pageInfoLabel.setText("");
                clearCanvas();
            }
        });

        // Prevent closing all accordion panes - keep at least one expanded
        chapterAccordion.expandedPaneProperty().addListener((obs, oldPane, newPane) -> {
            if (newPane == null && !chapterAccordion.getPanes().isEmpty()) {
                // If trying to close the last pane, keep the previous one expanded
                if (oldPane != null) {
                    chapterAccordion.setExpandedPane(oldPane);
                } else {
                    // If no previous pane, expand the first one
                    chapterAccordion.setExpandedPane(chapterAccordion.getPanes().get(0));
                }
            }
        });
    }

    private void loadChaptersForNotebook(String notebookId) {
        chapterAccordion.getPanes().clear();

        Notebook notebook = notebookService.getNotebook(notebookId);
        if (notebook == null) {
            return;
        }

        for (Chapter chapter : notebook.chapters()) {
            TitledPane chapterPane = createChapterPane(notebookId, chapter);
            chapterAccordion.getPanes().add(chapterPane);
        }

        // Expand the first pane if there are any chapters
        if (!chapterAccordion.getPanes().isEmpty()) {
            chapterAccordion.setExpandedPane(chapterAccordion.getPanes().get(0));
        }
    }

    private TitledPane createChapterPane(String notebookId, Chapter chapter) {
        TitledPane pane = new TitledPane();
        pane.setText(chapter.name());
        pane.setUserData(new ChapterPane(chapter.id()));

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
        }
    }

    private void clearCanvas() {
        if (canvasEditor != null) {
            canvasEditor.clear();
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
            updateStatus("Selection tool active");
        }
    }

    @FXML
    private void handleTextTool() {
        if (canvasEditor != null) {
            canvasEditor.setActiveTool("Text");
            updateStatus("Text tool active - Click to add text");
        }
    }

    @FXML
    private void handlePenTool() {
        if (canvasEditor != null) {
            canvasEditor.setActiveTool("Pen");
            updateStatus("Pen tool active - Draw on the canvas");
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
