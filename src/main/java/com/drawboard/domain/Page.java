package com.drawboard.domain;

import com.drawboard.domain.elements.CanvasElement;
import io.avaje.jsonb.Json;

import java.time.Instant;
import java.util.List;

/**
 * Represents a canvas page within a chapter.
 * A page contains canvas elements (text, images, drawings) positioned freely.
 */
@Json
public record Page(
    String id,
    String name,
    Instant created,
    Instant modified,
    List<CanvasElement> elements
) {}
