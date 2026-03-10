package com.drawboard.storage;

import com.drawboard.domain.Page;
import com.drawboard.domain.elements.DrawPath;
import com.drawboard.domain.elements.DrawingElement;
import com.drawboard.domain.elements.ImageElement;
import com.drawboard.domain.elements.Point;
import com.drawboard.domain.elements.TextElement;
import io.avaje.jsonb.Jsonb;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to verify JSON serialization format for domain models.
 */
class JsonSerializationTest {

    private final Jsonb jsonb = Jsonb.builder().build();

    @Test
    void testPageWithMixedElementsSerialization() {
        Instant now = Instant.now();

        TextElement textElement = new TextElement(
            UUID.randomUUID().toString(),
            10.0,
            20.0,
            300.0,
            150.0,
            "<p>Hello <strong>World</strong></p>",
            0
        );

        ImageElement imageElement = new ImageElement(
            UUID.randomUUID().toString(),
            400.0,
            100.0,
            200.0,
            200.0,
            "image-1.png",
            1
        );

        DrawingElement drawingElement = new DrawingElement(
            UUID.randomUUID().toString(),
            50.0,
            50.0,
            List.of(
                new DrawPath(
                    "#FF0000",
                    2.0,
                    List.of(
                        new Point(0.0, 0.0),
                        new Point(10.0, 10.0),
                        new Point(20.0, 5.0)
                    )
                )
            ),
            2
        );

        Page page = new Page(
            UUID.randomUUID().toString(),
            "Test Page",
            now,
            now,
            List.of(textElement, imageElement, drawingElement)
        );

        // Serialize
        String json = jsonb.type(Page.class).toJson(page);
        System.out.println("Serialized Page JSON:");
        System.out.println(json);

        // Verify it can be deserialized
        Page deserialized = jsonb.type(Page.class).fromJson(json);

        assertNotNull(deserialized);
        assertEquals(3, deserialized.elements().size());
        assertTrue(deserialized.elements().get(0) instanceof TextElement);
        assertTrue(deserialized.elements().get(1) instanceof ImageElement);
        assertTrue(deserialized.elements().get(2) instanceof DrawingElement);

        // Verify text element
        TextElement deserializedText = (TextElement) deserialized.elements().get(0);
        assertEquals(textElement.htmlContent(), deserializedText.htmlContent());
        assertEquals(textElement.x(), deserializedText.x());
        assertEquals(textElement.y(), deserializedText.y());

        // Verify image element
        ImageElement deserializedImage = (ImageElement) deserialized.elements().get(1);
        assertEquals(imageElement.filename(), deserializedImage.filename());

        // Verify drawing element
        DrawingElement deserializedDrawing = (DrawingElement) deserialized.elements().get(2);
        assertEquals(1, deserializedDrawing.paths().size());
        assertEquals(3, deserializedDrawing.paths().get(0).points().size());
    }
}
