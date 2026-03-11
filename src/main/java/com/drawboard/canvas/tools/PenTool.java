package com.drawboard.canvas.tools;

import com.drawboard.domain.elements.DrawPath;
import com.drawboard.domain.elements.DrawingElement;
import com.drawboard.domain.elements.Point;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
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
    private final Pane elementsPane;

    private Color currentColor = Color.BLACK;
    private double strokeWidth = 2.0;

    private boolean isDrawing;
    private List<Point> currentPath;
    private double pathStartX;
    private double pathStartY;
    private double lastX;
    private double lastY;

    private boolean isPanning;
    private double panStartX;
    private double panStartY;
    private double panStartTranslateX;
    private double panStartTranslateY;

    private Consumer<DrawingElement> onDrawingComplete;
    private List<Node> settingsNodes;
    private List<Button> colorButtons;

    public PenTool(Pane canvasContainer, Canvas drawingCanvas, Pane elementsPane) {
        this.canvasContainer = canvasContainer;
        this.drawingCanvas = drawingCanvas;
        this.elementsPane = elementsPane;
        this.gc = drawingCanvas.getGraphicsContext2D();
        this.settingsNodes = createSettingsNodes();
    }

    @Override
    public void onMousePressed(MouseEvent event) {
        // Middle button for panning
        if (event.isMiddleButtonDown()) {
            isPanning = true;
            panStartX = event.getX();
            panStartY = event.getY();
            panStartTranslateX = elementsPane.getTranslateX();
            panStartTranslateY = elementsPane.getTranslateY();
            canvasContainer.setCursor(Cursor.MOVE);
            log.debug("Starting pan (middle button) in Pen tool");
            event.consume();
            return;
        }

        // Only handle primary button (left click) for drawing
        if (!event.isPrimaryButtonDown()) {
            return;
        }

        isDrawing = true;
        currentPath = new ArrayList<>();

        // Get coordinates relative to the canvas container (event source)
        pathStartX = event.getX();
        pathStartY = event.getY();
        lastX = event.getX();
        lastY = event.getY();

        // Add first point
        currentPath.add(new Point(0, 0));

        // Set drawing properties
        gc.setStroke(currentColor);
        gc.setLineWidth(strokeWidth);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

        log.debug("Pen started at ({}, {}), canvas layout=({}, {}), color={}, canvas size={}x{}",
            event.getX(), event.getY(), drawingCanvas.getLayoutX(), drawingCanvas.getLayoutY(),
            currentColor, drawingCanvas.getWidth(), drawingCanvas.getHeight());

        event.consume();
    }

    @Override
    public void onMouseDragged(MouseEvent event) {
        if (isPanning) {
            // Pan the canvas
            double deltaX = event.getX() - panStartX;
            double deltaY = event.getY() - panStartY;
            elementsPane.setTranslateX(panStartTranslateX + deltaX);
            elementsPane.setTranslateY(panStartTranslateY + deltaY);
            event.consume();
            return;
        }

        if (!isDrawing) {
            return;
        }

        // Add point to path (relative to start)
        double relX = event.getX() - pathStartX;
        double relY = event.getY() - pathStartY;
        currentPath.add(new Point(relX, relY));

        // Draw line segment using strokeLine for immediate visual feedback
        gc.strokeLine(lastX, lastY, event.getX(), event.getY());

        lastX = event.getX();
        lastY = event.getY();

        if (currentPath.size() % 10 == 0) {
            log.debug("Drawing: {} points, last at ({}, {})", currentPath.size(), event.getX(), event.getY());
        }

        event.consume();
    }

    @Override
    public void onMouseReleased(MouseEvent event) {
        if (isPanning) {
            isPanning = false;
            canvasContainer.setCursor(Cursor.CROSSHAIR);
            log.debug("Pan complete in Pen tool");
            event.consume();
            return;
        }

        if (!isDrawing) {
            return;
        }

        isDrawing = false;

        // Create drawing element
        if (currentPath.size() > 1) {
            // Calculate actual bounding box of the path
            double minX = currentPath.stream().mapToDouble(Point::x).min().orElse(0);
            double minY = currentPath.stream().mapToDouble(Point::y).min().orElse(0);

            // Adjust element position to top-left of bounds
            // Account for elementsPane translation (pan offset)
            double translateX = elementsPane.getTranslateX();
            double translateY = elementsPane.getTranslateY();
            double elementX = pathStartX + minX - translateX;
            double elementY = pathStartY + minY - translateY;

            // Adjust all points relative to new element position
            List<Point> adjustedPoints = currentPath.stream()
                .map(p -> new Point(p.x() - minX, p.y() - minY))
                .toList();

            DrawPath drawPath = new DrawPath(
                toHexString(currentColor),
                strokeWidth,
                adjustedPoints
            );

            DrawingElement element = new DrawingElement(
                UUID.randomUUID().toString(),
                elementX,
                elementY,
                List.of(drawPath),
                1000  // High z-index so drawings appear on top
            );

            log.debug("Created drawing with {} points at ({}, {}) with translate ({}, {})",
                currentPath.size(), elementX, elementY, translateX, translateY);

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
        // Make elements pane mouse transparent so drawing events pass through
        elementsPane.setMouseTransparent(true);
        log.debug("Pen tool activated");
    }

    @Override
    public void deactivate() {
        isDrawing = false;
        currentPath = null;
        canvasContainer.setCursor(Cursor.DEFAULT);
        // Restore mouse events to elements pane
        elementsPane.setMouseTransparent(false);
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

    @Override
    public List<Node> getSettingsNodes() {
        return settingsNodes;
    }

    private List<Node> createSettingsNodes() {
        List<Node> nodes = new ArrayList<>();
        colorButtons = new ArrayList<>();

        // Add separator
        Separator separator = new Separator();
        separator.setOrientation(javafx.geometry.Orientation.VERTICAL);
        nodes.add(separator);

        // Add label
        Label label = new Label("Pen:");
        label.setStyle("-fx-padding: 0 10 0 10;");
        nodes.add(label);

        // Define colors
        List<ColorOption> colors = List.of(
            new ColorOption(Color.RED, "Red"),
            new ColorOption(Color.GREEN, "Green"),
            new ColorOption(Color.YELLOW, "Yellow"),
            new ColorOption(Color.BLUE, "Blue"),
            new ColorOption(Color.BLACK, "Black")
        );

        // Create color buttons
        for (ColorOption colorOption : colors) {
            Button colorButton = createColorButton(colorOption.color(), colorOption.name());
            colorButtons.add(colorButton);
            nodes.add(colorButton);
        }

        // Select black by default (last button)
        if (!colorButtons.isEmpty()) {
            updateButtonSelection(colorButtons.get(colorButtons.size() - 1));
        }

        return nodes;
    }

    private Button createColorButton(Color color, String tooltip) {
        Button button = new Button();
        button.setMinSize(24, 24);
        button.setMaxSize(24, 24);
        button.setPrefSize(24, 24);

        // Set background color
        button.setBackground(new Background(new BackgroundFill(
            color,
            new CornerRadii(3),
            Insets.EMPTY
        )));

        // Set tooltip
        button.setTooltip(new javafx.scene.control.Tooltip(tooltip));

        // Set action
        button.setOnAction(e -> {
            setColor(color);
            updateButtonSelection(button);
        });

        return button;
    }

    private void updateButtonSelection(Button selectedButton) {
        // Update all buttons to show selection state
        for (Button btn : colorButtons) {
            if (btn == selectedButton) {
                btn.setStyle("-fx-border-color: #2196F3; -fx-border-width: 2px; -fx-border-radius: 3px;");
            } else {
                btn.setStyle("-fx-border-color: #CCCCCC; -fx-border-width: 1px; -fx-border-radius: 3px;");
            }
        }
    }

    private record ColorOption(Color color, String name) {}
}
