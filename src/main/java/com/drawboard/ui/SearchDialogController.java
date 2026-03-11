package com.drawboard.ui;

import com.drawboard.domain.search.SearchMatchType;
import com.drawboard.domain.search.SearchOptions;
import com.drawboard.domain.search.SearchResult;
import com.drawboard.service.SearchService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Controller for the search dialog.
 * Allows searching across all notebooks or within the current notebook.
 */
public class SearchDialogController {
    private static final Logger log = LoggerFactory.getLogger(SearchDialogController.class);

    @FXML private TextField searchField;
    @FXML private CheckBox caseSensitiveCheckBox;
    @FXML private CheckBox wholeWordCheckBox;
    @FXML private CheckBox regexCheckBox;
    @FXML private RadioButton searchAllRadioButton;
    @FXML private RadioButton searchCurrentRadioButton;
    @FXML private Button searchButton;
    @FXML private ListView<SearchResultItem> resultsList;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private HBox searchOptionsBox;

    private final SearchService searchService;
    private String currentNotebookId;
    private Stage dialogStage;
    private Consumer<SearchResult> onResultSelected;

    private final ObservableList<SearchResultItem> results = FXCollections.observableArrayList();

    public SearchDialogController(SearchService searchService) {
        this.searchService = searchService;
    }

    @FXML
    public void initialize() {
        // Setup toggle group for search scope
        ToggleGroup scopeGroup = new ToggleGroup();
        searchAllRadioButton.setToggleGroup(scopeGroup);
        searchCurrentRadioButton.setToggleGroup(scopeGroup);
        searchAllRadioButton.setSelected(true);

        // Setup results list
        resultsList.setItems(results);
        resultsList.setCellFactory(param -> new SearchResultCell());

        // Handle result selection
        resultsList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                handleResultDoubleClick();
            }
        });

        // Handle Enter key in search field
        searchField.setOnKeyPressed(this::handleSearchFieldKeyPressed);

        // Handle Enter key in results list
        resultsList.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleResultDoubleClick();
            }
        });

        // Initially hide progress indicator
        progressIndicator.setVisible(false);

        updateStatus("Enter search terms and press Enter or click Search");
    }

    @FXML
    private void handleSearch() {
        String query = searchField.getText();
        if (query == null || query.isBlank()) {
            updateStatus("Please enter search terms");
            return;
        }

        // Clear previous results
        results.clear();

        // Build search options
        SearchOptions options = SearchOptions.builder()
            .caseSensitive(caseSensitiveCheckBox.isSelected())
            .wholeWord(wholeWordCheckBox.isSelected())
            .useRegex(regexCheckBox.isSelected())
            .maxResults(100)
            .snippetContextLength(50)
            .build();

        // Perform search in background
        performSearchAsync(query, options);
    }

    private void performSearchAsync(String query, SearchOptions options) {
        progressIndicator.setVisible(true);
        searchButton.setDisable(true);
        updateStatus("Searching...");

        Task<List<SearchResult>> searchTask = new Task<>() {
            @Override
            protected List<SearchResult> call() {
                if (searchAllRadioButton.isSelected()) {
                    return searchService.searchAllNotebooks(query, options);
                } else {
                    if (currentNotebookId != null) {
                        return searchService.searchNotebook(currentNotebookId, query, options);
                    } else {
                        return List.of();
                    }
                }
            }
        };

        searchTask.setOnSucceeded(event -> {
            List<SearchResult> searchResults = searchTask.getValue();
            displayResults(searchResults, query);
            progressIndicator.setVisible(false);
            searchButton.setDisable(false);
        });

        searchTask.setOnFailed(event -> {
            log.error("Search failed", searchTask.getException());
            updateStatus("Search failed: " + searchTask.getException().getMessage());
            progressIndicator.setVisible(false);
            searchButton.setDisable(false);
        });

        // Run task in background thread
        Thread searchThread = new Thread(searchTask);
        searchThread.setDaemon(true);
        searchThread.start();
    }

    private void displayResults(List<SearchResult> searchResults, String query) {
        results.clear();

        if (searchResults.isEmpty()) {
            updateStatus("No results found for: " + query);
            return;
        }

        for (SearchResult result : searchResults) {
            results.add(new SearchResultItem(result));
        }

        updateStatus(String.format("Found %d result(s) for: %s", results.size(), query));

        // Select first result
        if (!results.isEmpty()) {
            Platform.runLater(() -> {
                resultsList.getSelectionModel().selectFirst();
                resultsList.requestFocus();
            });
        }
    }

    private void handleResultDoubleClick() {
        SearchResultItem selected = resultsList.getSelectionModel().getSelectedItem();
        if (selected != null && onResultSelected != null) {
            onResultSelected.accept(selected.result());
            closeDialog();
        }
    }

    private void handleSearchFieldKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            handleSearch();
        } else if (event.getCode() == KeyCode.ESCAPE) {
            closeDialog();
        }
    }

    @FXML
    private void handleClose() {
        closeDialog();
    }

    private void closeDialog() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;

        // Handle Escape key to close dialog
        dialogStage.getScene().setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                closeDialog();
            }
        });
    }

    public void setCurrentNotebookId(String notebookId) {
        this.currentNotebookId = notebookId;
        searchCurrentRadioButton.setDisable(notebookId == null);
        if (notebookId == null) {
            searchAllRadioButton.setSelected(true);
        }
    }

    public void setOnResultSelected(Consumer<SearchResult> handler) {
        this.onResultSelected = handler;
    }

    public void focusSearchField() {
        Platform.runLater(() -> searchField.requestFocus());
    }

    // Custom cell for displaying search results
    private static class SearchResultCell extends ListCell<SearchResultItem> {
        @Override
        protected void updateItem(SearchResultItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                SearchResult result = item.result();
                String matchTypeIcon = getMatchTypeIcon(result.match().matchType());

                // Build multi-line display
                StringBuilder display = new StringBuilder();
                display.append(matchTypeIcon).append(" ");
                display.append(result.notebookName());

                if (result.chapterName() != null) {
                    display.append(" › ").append(result.chapterName());
                }

                if (result.pageName() != null) {
                    display.append(" › ").append(result.pageName());
                }

                display.append("\n");
                display.append("   ").append(result.match().snippet());

                setText(display.toString());
                setStyle("-fx-padding: 5 10 5 10;");
            }
        }

        private String getMatchTypeIcon(SearchMatchType type) {
            return switch (type) {
                case PAGE_NAME -> "📄";
                case CHAPTER_NAME -> "📁";
                case TEXT_ELEMENT -> "📝";
                case DRAWING_ANNOTATION -> "✏️";
                case IMAGE_TEXT -> "🖼️";
            };
        }
    }

    // Wrapper for search results in the ListView
    public record SearchResultItem(SearchResult result) {}
}
