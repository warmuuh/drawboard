package com.drawboard.domain;

import io.avaje.jsonb.Json;

import java.time.Instant;
import java.util.List;

/**
 * Represents a chapter within a notebook.
 * A chapter contains an ordered list of page IDs.
 */
@Json
public record Chapter(
    String id,
    String name,
    Instant created,
    Instant modified,
    List<String> pageIds
) {}
