package com.drawboard.domain.preferences;

import io.avaje.jsonb.Json;

/**
 * Canvas view settings (pan position, zoom, background color, etc).
 */
@Json
public record CanvasSettings(
    double translateX,
    double translateY,
    double zoom,  // for future zoom feature
    String backgroundColor  // hex color code (e.g., "#FFFACD")
) {
    public static CanvasSettings defaults() {
        // Light yellow color similar to American yellow legal pads
        return new CanvasSettings(0, 0, 1.0, "#FFFACD");
    }
}
