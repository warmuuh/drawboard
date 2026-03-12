package com.drawboard.app;

import com.drawboard.service.NotebookService;
import com.drawboard.service.ObsidianImportService;
import com.drawboard.service.PageService;
import com.drawboard.service.PreferencesService;
import com.drawboard.service.SearchService;
import com.drawboard.service.WebRTCShareService;
import com.drawboard.ui.MainWindowController;
import com.drawboard.util.SampleDataGenerator;
import io.avaje.inject.BeanScope;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application entry point for Drawboard.
 * Initializes the Avaje DI context and launches the JavaFX application.
 */
public class DrawboardApplication extends Application {
    private static final Logger log = LoggerFactory.getLogger(DrawboardApplication.class);

    private BeanScope beanScope;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() {
        // Initialize Avaje DI container
        beanScope = BeanScope.builder().build();
        log.debug("Dependency injection context initialized");
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Drawboard");

        // Set application icon
        try {
            Image icon = new Image(getClass().getResourceAsStream("/icons/drawboard.png"));
            primaryStage.getIcons().add(icon);
        } catch (Exception e) {
            log.warn("Failed to load application icon", e);
        }

        // Get services from DI container
        NotebookService notebookService = beanScope.get(NotebookService.class);
        PageService pageService = beanScope.get(PageService.class);
        SampleDataGenerator sampleDataGenerator = beanScope.get(SampleDataGenerator.class);

        // Create sample data if this is first run
        if (notebookService.getAllNotebooks().isEmpty()) {
            sampleDataGenerator.createSampleNotebook();
        }

        // Get services
        PreferencesService preferencesService = beanScope.get(PreferencesService.class);
        ObsidianImportService obsidianImportService = beanScope.get(ObsidianImportService.class);
        SearchService searchService = beanScope.get(SearchService.class);
        WebRTCShareService webrtcService = beanScope.get(WebRTCShareService.class);

        // Load FXML with controller factory
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainWindow.fxml"));
        MainWindowController mainController = new MainWindowController(notebookService, pageService, preferencesService,
            obsidianImportService, searchService, webrtcService);
        loader.setControllerFactory(clazz -> {
            if (clazz == MainWindowController.class) {
                return mainController;
            }
            throw new IllegalArgumentException("Unexpected controller class: " + clazz);
        });

        Parent root = loader.load();
        Scene scene = new Scene(root, 1200, 800);

        primaryStage.setScene(scene);

        // Set up close handler to clean up share dialogs
        primaryStage.setOnCloseRequest(event -> {
            mainController.cleanup();
        });

        primaryStage.show();

        log.info("Drawboard application started");
    }

    @Override
    public void stop() {
        log.info("Shutting down Drawboard application");
        if (beanScope != null) {
            beanScope.close();
        }
    }
}
