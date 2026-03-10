package com.drawboard.domain.preferences;

import io.avaje.jsonb.Json;

/**
 * Canvas view settings (pan position, zoom, etc).
 */
@Json
public record CanvasSettings(
    double translateX,
    double translateY,
    double zoom  // for future zoom feature
) {
    public static CanvasSettings defaults() {
        return new CanvasSettings(0, 0, 1.0);
    }
}
