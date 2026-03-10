package com.drawboard.domain.preferences;

import io.avaje.jsonb.Json;

/**
 * User preferences for the Drawboard application.
 * Stored in a platform-appropriate location.
 */
@Json
public record UserPreferences(
    LastOpenedPage lastOpenedPage,
    WindowState windowState,
    String theme,
    CanvasSettings canvasSettings
) {
    public static UserPreferences defaults() {
        return new UserPreferences(null, null, "system", null);
    }
}
