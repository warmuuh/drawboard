package com.drawboard.canvas;

import com.drawboard.domain.elements.DrawPath;
import com.drawboard.domain.elements.DrawingElement;
import com.drawboard.domain.elements.Point;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Renders drawing elements (freehand paths) as JavaFX nodes.
 * This allows drawings to be z-ordered with other elements.
 */
public class DrawingElementRenderer {

    public Node render(DrawingElement element) {
        Group group = new Group();
        group.setLayoutX(element.x());
        group.setLayoutY(element.y());

        for (DrawPath path : element.paths()) {
            Polyline polyline = renderPath(path);
            group.getChildren().add(polyline);
        }

        return group;
    }

    private Polyline renderPath(DrawPath path) {
        if (path.points().isEmpty()) {
            return new Polyline();
        }

        Polyline polyline = new Polyline();

        // Add all points
        for (Point point : path.points()) {
            polyline.getPoints().addAll(point.x(), point.y());
        }

        // Set stroke properties
        polyline.setStroke(Color.web(path.color()));
        polyline.setStrokeWidth(path.strokeWidth());
        polyline.setStrokeLineCap(StrokeLineCap.ROUND);
        polyline.setStrokeLineJoin(StrokeLineJoin.ROUND);
        polyline.setFill(null);

        return polyline;
    }
}
