package com.drawboard.ui;

import com.drawboard.domain.Chapter;
import com.drawboard.domain.Notebook;
import com.drawboard.domain.Page;
import com.drawboard.service.NotebookService;
import com.drawboard.service.PageService;
import com.drawboard.service.PreferencesService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
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

    @FXML private TreeView<TreeItemData> navigationTree;
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

    private TreeItem<TreeItemData> selectedNotebookItem;
    private TreeItem<TreeItemData> selectedChapterItem;

    private CanvasEditorController canvasEditor;

    public MainWindowController(NotebookService notebookService, PageService pageService, PreferencesService preferencesService) {
        this.notebookService = notebookService;
        this.pageService = pageService;
        this.preferencesService = preferencesService;
    }

    @FXML
    public void initialize() {
        // Initialize canvas editor
        canvasEditor = new CanvasEditorController(pageService, preferencesService, canvasArea);

        setupNavigationTree();
        setupToolButtons();
        loadNotebooks();

        // Restore last opened page
        restoreLastOpenedPage();

        updateStatus("Ready");
    }

    private void restoreLastOpenedPage() {
        preferencesService.getLastOpenedPage().ifPresent(lastPage -> {
            log.info("Restoring last opened page: {}/{}/{}", lastPage.notebookId(), lastPage.chapterId(), lastPage.pageId());

            // Find and select the page in the tree
            TreeItem<TreeItemData> pageItem = findPageInTree(lastPage.notebookId(), lastPage.chapterId(), lastPage.pageId());
            if (pageItem != null) {
                // Expand parents and select
                expandToItem(pageItem);
                navigationTree.getSelectionModel().select(pageItem);
                log.info("Restored last opened page successfully");
            } else {
                log.warn("Last opened page not found in navigation tree");
            }
        });
    }

    private TreeItem<TreeItemData> findPageInTree(String notebookId, String chapterId, String pageId) {
        for (TreeItem<TreeItemData> notebookItem : navigationTree.getRoot().getChildren()) {
            if (notebookItem.getValue().id().equals(notebookId)) {
                for (TreeItem<TreeItemData> chapterItem : notebookItem.getChildren()) {
                    if (chapterItem.getValue().id().equals(chapterId)) {
                        for (TreeItem<TreeItemData> pageItem : chapterItem.getChildren()) {
                            if (pageItem.getValue().id().equals(pageId)) {
                                return pageItem;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private void expandToItem(TreeItem<TreeItemData> item) {
        if (item.getParent() != null) {
            expandToItem(item.getParent());
            item.getParent().setExpanded(true);
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

    private void setupNavigationTree() {
        // Create root item (hidden)
        TreeItem<TreeItemData> root = new TreeItem<>(new TreeItemData(TreeItemType.ROOT, null, "Root"));
        root.setExpanded(true);
        navigationTree.setRoot(root);
        navigationTree.setShowRoot(false);

        // Setup selection listener
        navigationTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            handleTreeSelection(newVal);
        });

        // Setup context menu
        navigationTree.setContextMenu(createContextMenu());
    }

    private ContextMenu createContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem newNotebook = new MenuItem("New Notebook");
        newNotebook.setOnAction(e -> handleNewNotebook());

        MenuItem newChapter = new MenuItem("New Chapter");
        newChapter.setOnAction(e -> handleNewChapter());

        MenuItem newPage = new MenuItem("New Page");
        newPage.setOnAction(e -> handleNewPage());

        MenuItem rename = new MenuItem("Rename");
        rename.setOnAction(e -> handleRename());

        MenuItem delete = new MenuItem("Delete");
        delete.setOnAction(e -> handleDelete());

        contextMenu.getItems().addAll(newNotebook, newChapter, newPage, new SeparatorMenuItem(), rename, delete);

        // Show/hide menu items based on selection
        contextMenu.setOnShowing(e -> {
            TreeItem<TreeItemData> selected = navigationTree.getSelectionModel().getSelectedItem();
            if (selected == null || selected.getValue().type() == TreeItemType.ROOT) {
                newNotebook.setVisible(true);
                newChapter.setVisible(false);
                newPage.setVisible(false);
                rename.setVisible(false);
                delete.setVisible(false);
            } else if (selected.getValue().type() == TreeItemType.NOTEBOOK) {
                newNotebook.setVisible(true);
                newChapter.setVisible(true);
                newPage.setVisible(false);
                rename.setVisible(true);
                delete.setVisible(true);
            } else if (selected.getValue().type() == TreeItemType.CHAPTER) {
                newNotebook.setVisible(true);
                newChapter.setVisible(true);
                newPage.setVisible(true);
                rename.setVisible(true);
                delete.setVisible(true);
            } else if (selected.getValue().type() == TreeItemType.PAGE) {
                newNotebook.setVisible(true);
                newChapter.setVisible(false);
                newPage.setVisible(true);
                rename.setVisible(true);
                delete.setVisible(true);
            }
        });

        return contextMenu;
    }

    private void handleTreeSelection(TreeItem<TreeItemData> selected) {
        if (selected == null) {
            btnNewChapter.setDisable(true);
            btnNewPage.setDisable(true);
            pageInfoLabel.setText("");
            return;
        }

        TreeItemData data = selected.getValue();

        switch (data.type()) {
            case NOTEBOOK -> {
                selectedNotebookItem = selected;
                selectedChapterItem = null;
                btnNewChapter.setDisable(false);
                btnNewPage.setDisable(true);
                pageInfoLabel.setText("Notebook: " + data.name());
                clearCanvas();
            }
            case CHAPTER -> {
                selectedNotebookItem = selected.getParent();
                selectedChapterItem = selected;
                btnNewChapter.setDisable(false);
                btnNewPage.setDisable(false);
                pageInfoLabel.setText("Chapter: " + data.name());
                clearCanvas();
            }
            case PAGE -> {
                selectedChapterItem = selected.getParent();
                selectedNotebookItem = selectedChapterItem.getParent();
                btnNewChapter.setDisable(false);
                btnNewPage.setDisable(false);
                loadPage(data);
            }
            default -> {
                btnNewChapter.setDisable(true);
                btnNewPage.setDisable(true);
                pageInfoLabel.setText("");
            }
        }
    }

    private void loadNotebooks() {
        TreeItem<TreeItemData> root = navigationTree.getRoot();
        root.getChildren().clear();

        List<Notebook> notebooks = notebookService.getAllNotebooks();

        for (Notebook notebook : notebooks) {
            TreeItem<TreeItemData> notebookItem = createNotebookItem(notebook);
            root.getChildren().add(notebookItem);
        }

        updateStatus("Loaded " + notebooks.size() + " notebook(s)");
    }

    private TreeItem<TreeItemData> createNotebookItem(Notebook notebook) {
        TreeItem<TreeItemData> notebookItem = new TreeItem<>(
            new TreeItemData(TreeItemType.NOTEBOOK, notebook.id(), notebook.name())
        );
        notebookItem.setExpanded(false);

        for (Chapter chapter : notebook.chapters()) {
            TreeItem<TreeItemData> chapterItem = createChapterItem(notebook.id(), chapter);
            notebookItem.getChildren().add(chapterItem);
        }

        return notebookItem;
    }

    private TreeItem<TreeItemData> createChapterItem(String notebookId, Chapter chapter) {
        TreeItem<TreeItemData> chapterItem = new TreeItem<>(
            new TreeItemData(TreeItemType.CHAPTER, chapter.id(), chapter.name())
        );
        chapterItem.setExpanded(false);

        log.debug("Loading chapter {} with {} pageIds", chapter.name(), chapter.pageIds().size());

        for (String pageId : chapter.pageIds()) {
            Page page = pageService.getPage(notebookId, chapter.id(), pageId);
            if (page != null) {
                TreeItem<TreeItemData> pageItem = new TreeItem<>(
                    new TreeItemData(TreeItemType.PAGE, page.id(), page.name())
                );
                chapterItem.getChildren().add(pageItem);
                log.debug("Added page: {}", page.name());
            } else {
                log.warn("Page not found: {} in chapter {}", pageId, chapter.id());
            }
        }

        return chapterItem;
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
                TreeItem<TreeItemData> notebookItem = createNotebookItem(notebook);
                navigationTree.getRoot().getChildren().add(notebookItem);
                navigationTree.getSelectionModel().select(notebookItem);
                updateStatus("Created notebook: " + name);
            }
        });
    }

    @FXML
    private void handleNewChapter() {
        if (selectedNotebookItem == null) {
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
                String notebookId = selectedNotebookItem.getValue().id();
                Chapter chapter = notebookService.createChapter(notebookId, name);
                TreeItem<TreeItemData> chapterItem = createChapterItem(notebookId, chapter);
                selectedNotebookItem.getChildren().add(chapterItem);
                selectedNotebookItem.setExpanded(true);
                navigationTree.getSelectionModel().select(chapterItem);
                updateStatus("Created chapter: " + name);
            }
        });
    }

    @FXML
    private void handleNewPage() {
        if (selectedNotebookItem == null || selectedChapterItem == null) {
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
                String notebookId = selectedNotebookItem.getValue().id();
                String chapterId = selectedChapterItem.getValue().id();
                Page page = pageService.createPage(notebookId, chapterId, name);
                TreeItem<TreeItemData> pageItem = new TreeItem<>(
                    new TreeItemData(TreeItemType.PAGE, page.id(), page.name())
                );
                selectedChapterItem.getChildren().add(pageItem);
                selectedChapterItem.setExpanded(true);
                navigationTree.getSelectionModel().select(pageItem);
                updateStatus("Created page: " + name);
            }
        });
    }

    private void handleRename() {
        TreeItem<TreeItemData> selected = navigationTree.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        TreeItemData data = selected.getValue();
        TextInputDialog dialog = new TextInputDialog(data.name());
        dialog.setTitle("Rename");
        dialog.setHeaderText("Rename " + data.type().toString().toLowerCase());
        dialog.setContentText("New name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newName -> {
            if (!newName.isBlank() && !newName.equals(data.name())) {
                try {
                    switch (data.type()) {
                        case NOTEBOOK -> {
                            notebookService.renameNotebook(data.id(), newName);
                            selected.setValue(new TreeItemData(TreeItemType.NOTEBOOK, data.id(), newName));
                        }
                        case CHAPTER -> {
                            String notebookId = selected.getParent().getValue().id();
                            notebookService.renameChapter(notebookId, data.id(), newName);
                            selected.setValue(new TreeItemData(TreeItemType.CHAPTER, data.id(), newName));
                        }
                        case PAGE -> {
                            String notebookId = selected.getParent().getParent().getValue().id();
                            String chapterId = selected.getParent().getValue().id();
                            pageService.renamePage(notebookId, chapterId, data.id(), newName);
                            selected.setValue(new TreeItemData(TreeItemType.PAGE, data.id(), newName));
                        }
                    }
                    updateStatus("Renamed to: " + newName);
                } catch (Exception e) {
                    log.error("Failed to rename", e);
                    showError("Failed to rename: " + e.getMessage());
                }
            }
        });
    }

    private void handleDelete() {
        TreeItem<TreeItemData> selected = navigationTree.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        TreeItemData data = selected.getValue();
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Delete");
        alert.setHeaderText("Delete " + data.type().toString().toLowerCase() + ": " + data.name());
        alert.setContentText("This action cannot be undone. Are you sure?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                switch (data.type()) {
                    case NOTEBOOK -> notebookService.deleteNotebook(data.id());
                    case CHAPTER -> {
                        String notebookId = selected.getParent().getValue().id();
                        notebookService.deleteChapter(notebookId, data.id());
                    }
                    case PAGE -> {
                        String notebookId = selected.getParent().getParent().getValue().id();
                        String chapterId = selected.getParent().getValue().id();
                        pageService.deletePage(notebookId, chapterId, data.id());
                    }
                }
                selected.getParent().getChildren().remove(selected);
                updateStatus("Deleted: " + data.name());
            } catch (Exception e) {
                log.error("Failed to delete", e);
                showError("Failed to delete: " + e.getMessage());
            }
        }
    }

    private void loadPage(TreeItemData pageData) {
        String notebookId = selectedNotebookItem.getValue().id();
        String chapterId = selectedChapterItem.getValue().id();

        Page page = pageService.getPage(notebookId, chapterId, pageData.id());
        if (page != null) {
            pageInfoLabel.setText("Page: " + page.name() + " (" + page.elements().size() + " elements)");

            // Load page into canvas editor (it will handle clearing)
            canvasEditor.loadPage(notebookId, chapterId, pageData.id());
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

    // Data class for tree items
    public record TreeItemData(TreeItemType type, String id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    public enum TreeItemType {
        ROOT, NOTEBOOK, CHAPTER, PAGE
    }
}
