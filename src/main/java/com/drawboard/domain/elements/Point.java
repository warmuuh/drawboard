package com.drawboard.domain.elements;

import io.avaje.jsonb.Json;

/**
 * Represents a point in 2D space.
 */
@Json
public record Point(double x, double y) {}
