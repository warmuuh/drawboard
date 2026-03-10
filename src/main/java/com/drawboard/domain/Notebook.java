package com.drawboard.domain;

import io.avaje.jsonb.Json;

import java.time.Instant;
import java.util.List;

/**
 * Represents a notebook containing chapters.
 * A notebook is the top-level organizational unit in Drawboard.
 */
@Json
public record Notebook(
    String id,
    String name,
    Instant created,
    Instant modified,
    List<Chapter> chapters
) {}
