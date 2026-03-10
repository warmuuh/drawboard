package com.drawboard.domain.elements;

import io.avaje.jsonb.Json;

/**
 * Represents an image element on the canvas.
 * The image is stored as a file in the page directory and referenced by filename.
 */
@Json
public record ImageElement(
    String id,
    double x,
    double y,
    double width,
    double height,
    String filename,
    int zIndex
) implements CanvasElement {}
