package com.drawboard.canvas.tools;

import com.drawboard.domain.elements.TextElement;
import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Text tool for adding text elements to the canvas.
 * Click to create a new text box at the click location.
 */
public class TextTool implements Tool {
    private static final Logger log = LoggerFactory.getLogger(TextTool.class);

    private final Pane canvasContainer;
    private Consumer<TextElement> onTextElementCreated;

    private static final double DEFAULT_WIDTH = 300;
    private static final double DEFAULT_HEIGHT = 100;

    public TextTool(Pane canvasContainer) {
        this.canvasContainer = canvasContainer;
    }

    @Override
    public void onMousePressed(MouseEvent event) {
        // Create text element at click position
        TextElement element = new TextElement(
            UUID.randomUUID().toString(),
            event.getX(),
            event.getY(),
            DEFAULT_WIDTH,
            DEFAULT_HEIGHT,
            "<p>Type your text here...</p>",
            0  // TODO: Calculate proper z-index
        );

        log.debug("Created text element at ({}, {})", event.getX(), event.getY());

        // Notify listener
        if (onTextElementCreated != null) {
            onTextElementCreated.accept(element);
        }

        event.consume();
    }

    @Override
    public void onMouseDragged(MouseEvent event) {
        // No-op for text tool
    }

    @Override
    public void onMouseReleased(MouseEvent event) {
        // No-op for text tool
    }

    @Override
    public void activate() {
        canvasContainer.setCursor(Cursor.TEXT);
        log.debug("Text tool activated");
    }

    @Override
    public void deactivate() {
        canvasContainer.setCursor(Cursor.DEFAULT);
        log.debug("Text tool deactivated");
    }

    @Override
    public String getName() {
        return "Text";
    }

    public void setOnTextElementCreated(Consumer<TextElement> listener) {
        this.onTextElementCreated = listener;
    }
}
