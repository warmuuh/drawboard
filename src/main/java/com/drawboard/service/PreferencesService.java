package com.drawboard.service;

import com.drawboard.domain.preferences.CanvasSettings;
import com.drawboard.domain.preferences.LastOpenedPage;
import com.drawboard.domain.preferences.UserPreferences;
import com.drawboard.domain.preferences.WindowState;
import io.avaje.jsonb.Jsonb;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * Manages user preferences storage and retrieval.
 * Preferences are stored in a platform-appropriate location.
 */
@Singleton
public class PreferencesService {
    private static final Logger log = LoggerFactory.getLogger(PreferencesService.class);

    private final Path preferencesFile;
    private final Jsonb jsonb;
    private UserPreferences currentPreferences;

    public PreferencesService(Jsonb jsonb) {
        this.jsonb = jsonb;
        this.preferencesFile = getPreferencesPath();
        this.currentPreferences = load();
    }

    /**
     * Get platform-appropriate preferences file path.
     */
    private Path getPreferencesPath() {
        String osName = System.getProperty("os.name").toLowerCase();
        String appData;

        if (osName.contains("mac")) {
            appData = System.getProperty("user.home") + "/Library/Application Support";
        } else if (osName.contains("win")) {
            appData = System.getenv("APPDATA");
            if (appData == null) {
                appData = System.getProperty("user.home") + "/AppData/Roaming";
            }
        } else {
            // Linux and others
            String xdgConfig = System.getenv("XDG_CONFIG_HOME");
            if (xdgConfig != null && !xdgConfig.isEmpty()) {
                appData = xdgConfig;
            } else {
                appData = System.getProperty("user.home") + "/.config";
            }
        }

        return Path.of(appData, "Drawboard", "preferences.json");
    }

    /**
     * Load preferences from disk.
     */
    private UserPreferences load() {
        if (!Files.exists(preferencesFile)) {
            log.info("No preferences file found, using defaults");
            return UserPreferences.defaults();
        }

        try {
            String json = Files.readString(preferencesFile);
            UserPreferences prefs = jsonb.type(UserPreferences.class).fromJson(json);
            log.info("Loaded preferences from: {}", preferencesFile);
            return prefs;
        } catch (IOException e) {
            log.warn("Failed to load preferences, using defaults: {}", e.getMessage());
            return UserPreferences.defaults();
        }
    }

    /**
     * Save current preferences to disk.
     */
    private void save() {
        try {
            Files.createDirectories(preferencesFile.getParent());
            String json = jsonb.type(UserPreferences.class).toJson(currentPreferences);
            Files.writeString(preferencesFile, json);
            log.debug("Saved preferences to: {}", preferencesFile);
        } catch (IOException e) {
            log.error("Failed to save preferences: {}", e.getMessage(), e);
        }
    }

    // ==================== Last Opened Page ====================

    public void saveLastOpenedPage(String notebookId, String chapterId, String pageId) {
        LastOpenedPage lastPage = new LastOpenedPage(notebookId, chapterId, pageId, Instant.now());
        currentPreferences = new UserPreferences(
            lastPage,
            currentPreferences.windowState(),
            currentPreferences.theme(),
            currentPreferences.canvasSettings()
        );
        save();
        log.debug("Saved last opened page: {}/{}/{}", notebookId, chapterId, pageId);
    }

    public Optional<LastOpenedPage> getLastOpenedPage() {
        return Optional.ofNullable(currentPreferences.lastOpenedPage());
    }

    // ==================== Window State ====================

    public void saveWindowState(double x, double y, double width, double height, boolean maximized) {
        WindowState windowState = new WindowState(x, y, width, height, maximized);
        currentPreferences = new UserPreferences(
            currentPreferences.lastOpenedPage(),
            windowState,
            currentPreferences.theme(),
            currentPreferences.canvasSettings()
        );
        save();
        log.debug("Saved window state: {}x{} at ({}, {})", width, height, x, y);
    }

    public Optional<WindowState> getWindowState() {
        return Optional.ofNullable(currentPreferences.windowState());
    }

    // ==================== Canvas Settings ====================

    public void saveCanvasSettings(double translateX, double translateY, double zoom) {
        CanvasSettings canvasSettings = new CanvasSettings(translateX, translateY, zoom);
        currentPreferences = new UserPreferences(
            currentPreferences.lastOpenedPage(),
            currentPreferences.windowState(),
            currentPreferences.theme(),
            canvasSettings
        );
        save();
        log.debug("Saved canvas settings: translate({}, {}), zoom={}", translateX, translateY, zoom);
    }

    public CanvasSettings getCanvasSettings() {
        CanvasSettings settings = currentPreferences.canvasSettings();
        return settings != null ? settings : CanvasSettings.defaults();
    }

    // ==================== Theme ====================

    public void saveTheme(String theme) {
        currentPreferences = new UserPreferences(
            currentPreferences.lastOpenedPage(),
            currentPreferences.windowState(),
            theme,
            currentPreferences.canvasSettings()
        );
        save();
        log.debug("Saved theme: {}", theme);
    }

    public String getTheme() {
        String theme = currentPreferences.theme();
        return theme != null ? theme : "system";
    }

    // ==================== Utility ====================

    public UserPreferences getCurrentPreferences() {
        return currentPreferences;
    }
}
