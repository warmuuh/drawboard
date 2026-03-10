package com.drawboard.domain.preferences;

import io.avaje.jsonb.Json;

import java.util.Map;

/**
 * User preferences for the Drawboard application.
 * Stored in a platform-appropriate location.
 */
@Json
public record UserPreferences(
    LastOpenedPage lastOpenedPage,
    Map<String, LastOpenedPage> lastOpenedPagePerNotebook,
    WindowState windowState,
    Double splitPaneDividerPosition,
    String theme,
    CanvasSettings canvasSettings
) {
    public static UserPreferences defaults() {
        return new UserPreferences(null, Map.of(), null, 0.2, "system", null);
    }
}
