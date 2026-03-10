package com.drawboard.domain.preferences;

import io.avaje.jsonb.Json;

/**
 * Window position and size state for restoration.
 */
@Json
public record WindowState(
    double x,
    double y,
    double width,
    double height,
    boolean maximized
) {}
