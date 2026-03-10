package com.drawboard.domain.elements;

import io.avaje.jsonb.Json;

/**
 * Represents a text element on the canvas with rich text formatting.
 * Text content is stored as HTML for rich formatting support.
 */
@Json
public record TextElement(
    String id,
    double x,
    double y,
    double width,
    double height,
    String htmlContent,
    int zIndex
) implements CanvasElement {}
