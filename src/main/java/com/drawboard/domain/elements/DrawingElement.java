package com.drawboard.domain.elements;

import io.avaje.jsonb.Json;

import java.util.List;

/**
 * Represents a freehand drawing element on the canvas.
 * A drawing consists of multiple paths, each with its own color and stroke width.
 */
@Json
public record DrawingElement(
    String id,
    double x,
    double y,
    List<DrawPath> paths,
    int zIndex
) implements CanvasElement {}
