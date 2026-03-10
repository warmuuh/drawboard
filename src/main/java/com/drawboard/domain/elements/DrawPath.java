package com.drawboard.domain.elements;

import io.avaje.jsonb.Json;

import java.util.List;

/**
 * Represents a single stroke path within a drawing element.
 */
@Json
public record DrawPath(
    String color,
    double strokeWidth,
    List<Point> points
) {}
