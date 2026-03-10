package com.drawboard.canvas.tools;

import com.drawboard.domain.elements.DrawPath;
import com.drawboard.domain.elements.DrawingElement;
import com.drawboard.domain.elements.Point;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Pen tool for freehand drawing on the canvas.
 */
public class PenTool implements Tool {
    private static final Logger log = LoggerFactory.getLogger(PenTool.class);

    private final Pane canvasContainer;
    private final Canvas drawingCanvas;
    private final GraphicsContext gc;

    private Color currentColor = Color.BLACK;
    private double strokeWidth = 2.0;

    private boolean isDrawing;
    private List<Point> currentPath;
    private double pathStartX;
    private double pathStartY;

    private Consumer<DrawingElement> onDrawingComplete;

    public PenTool(Pane canvasContainer, Canvas drawingCanvas) {
        this.canvasContainer = canvasContainer;
        this.drawingCanvas = drawingCanvas;
        this.gc = drawingCanvas.getGraphicsContext2D();
    }

    @Override
    public void onMousePressed(MouseEvent event) {
        isDrawing = true;
        currentPath = new ArrayList<>();

        pathStartX = event.getX();
        pathStartY = event.getY();

        // Add first point
        currentPath.add(new Point(0, 0));

        // Set drawing properties
        gc.setStroke(currentColor);
        gc.setLineWidth(strokeWidth);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

        gc.beginPath();
        gc.moveTo(event.getX(), event.getY());

        event.consume();
    }

    @Override
    public void onMouseDragged(MouseEvent event) {
        if (!isDrawing) {
            return;
        }

        // Add point to path (relative to start)
        double relX = event.getX() - pathStartX;
        double relY = event.getY() - pathStartY;
        currentPath.add(new Point(relX, relY));

        // Draw line segment
        gc.lineTo(event.getX(), event.getY());
        gc.stroke();

        event.consume();
    }

    @Override
    public void onMouseReleased(MouseEvent event) {
        if (!isDrawing) {
            return;
        }

        isDrawing = false;

        // Create drawing element
        if (currentPath.size() > 1) {
            DrawPath drawPath = new DrawPath(
                toHexString(currentColor),
                strokeWidth,
                new ArrayList<>(currentPath)
            );

            DrawingElement element = new DrawingElement(
                UUID.randomUUID().toString(),
                pathStartX,
                pathStartY,
                List.of(drawPath),
                1000  // High z-index so drawings appear on top
            );

            log.debug("Created drawing with {} points", currentPath.size());

            // Notify listener
            if (onDrawingComplete != null) {
                onDrawingComplete.accept(element);
            }
        }

        currentPath = null;

        event.consume();
    }

    @Override
    public void activate() {
        canvasContainer.setCursor(Cursor.CROSSHAIR);
        log.debug("Pen tool activated");
    }

    @Override
    public void deactivate() {
        isDrawing = false;
        currentPath = null;
        canvasContainer.setCursor(Cursor.DEFAULT);
        log.debug("Pen tool deactivated");
    }

    @Override
    public String getName() {
        return "Pen";
    }

    public void setColor(Color color) {
        this.currentColor = color;
    }

    public void setStrokeWidth(double width) {
        this.strokeWidth = width;
    }

    public void setOnDrawingComplete(Consumer<DrawingElement> listener) {
        this.onDrawingComplete = listener;
    }

    private String toHexString(Color color) {
        return String.format("#%02X%02X%02X",
            (int) (color.getRed() * 255),
            (int) (color.getGreen() * 255),
            (int) (color.getBlue() * 255));
    }
}
