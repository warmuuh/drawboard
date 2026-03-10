package com.drawboard.domain.elements;

import io.avaje.jsonb.Json;

/**
 * Sealed interface representing any element that can be placed on a canvas page.
 * Subtypes include text, images, and freehand drawings.
 */
@Json
@Json.SubType(type = TextElement.class, name = "TEXT")
@Json.SubType(type = ImageElement.class, name = "IMAGE")
@Json.SubType(type = DrawingElement.class, name = "DRAWING")
public sealed interface CanvasElement
    permits TextElement, ImageElement, DrawingElement {

    String id();
    double x();
    double y();
    int zIndex();
}
