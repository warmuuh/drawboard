package com.drawboard.canvas;

import com.drawboard.domain.elements.DrawPath;
import com.drawboard.domain.elements.DrawingElement;
import com.drawboard.domain.elements.Point;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Renders drawing elements (freehand paths) on a JavaFX Canvas.
 */
public class DrawingElementRenderer {

    private final GraphicsContext gc;

    public DrawingElementRenderer(GraphicsContext gc) {
        this.gc = gc;
    }

    public void render(DrawingElement element) {
        for (DrawPath path : element.paths()) {
            renderPath(element, path);
        }
    }

    private void renderPath(DrawingElement element, DrawPath path) {
        if (path.points().isEmpty()) {
            return;
        }

        // Set stroke properties
        gc.setStroke(Color.web(path.color()));
        gc.setLineWidth(path.strokeWidth());
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

        // Draw path
        gc.beginPath();

        Point firstPoint = path.points().get(0);
        gc.moveTo(element.x() + firstPoint.x(), element.y() + firstPoint.y());

        for (int i = 1; i < path.points().size(); i++) {
            Point point = path.points().get(i);
            gc.lineTo(element.x() + point.x(), element.y() + point.y());
        }

        gc.stroke();
    }
}
