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
 * Highlighter tool for marking text passages with transparent neon colors.
 */
public class HighlighterTool extends AbstractTool {
    private static final Logger log = LoggerFactory.getLogger(HighlighterTool.class);

    private final Canvas drawingCanvas;
    private final GraphicsContext gc;

    private Color currentColor = Color.rgb(255, 255, 0, 0.3); // Yellow with transparency
    private static final double STROKE_WIDTH = 20.0; // Wider stroke for highlighter effect

    private boolean isDrawing;
    private List<Point> currentPath;
    private double pathStartX;
    private double pathStartY;
    private double lastX;
    private double lastY;

    private Consumer<DrawingElement> onDrawingComplete;
    private List<Node> settingsNodes;
    private List<Button> colorButtons;

    public HighlighterTool(Pane canvasContainer, Canvas drawingCanvas, Pane elementsPane) {
        super(canvasContainer, elementsPane);
        this.drawingCanvas = drawingCanvas;
        this.gc = drawingCanvas.getGraphicsContext2D();
        this.settingsNodes = createSettingsNodes();
    }

    @Override
    protected void handleMousePressed(MouseEvent event) {
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

        // Set drawing properties with transparency
        gc.setStroke(currentColor);
        gc.setLineWidth(STROKE_WIDTH);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

        log.debug("Highlighter started at ({}, {}), color={}", event.getX(), event.getY(), currentColor);

        event.consume();
    }

    @Override
    protected void handleMouseDragged(MouseEvent event) {
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
            log.debug("Highlighting: {} points, last at ({}, {})", currentPath.size(), event.getX(), event.getY());
        }

        event.consume();
    }

    @Override
    protected void handleMouseReleased(MouseEvent event) {
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
                toHexStringWithAlpha(currentColor),
                STROKE_WIDTH,
                adjustedPoints
            );

            DrawingElement element = new DrawingElement(
                UUID.randomUUID().toString(),
                elementX,
                elementY,
                List.of(drawPath),
                500  // Lower z-index so highlighter appears below text/pen drawings
            );

            log.debug("Created highlighter stroke with {} points at ({}, {}) with translate ({}, {})",
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
        log.debug("Highlighter tool activated");
    }

    @Override
    public void deactivate() {
        isDrawing = false;
        currentPath = null;
        canvasContainer.setCursor(Cursor.DEFAULT);
        // Restore mouse events to elements pane
        elementsPane.setMouseTransparent(false);
        log.debug("Highlighter tool deactivated");
    }

    @Override
    public String getName() {
        return "Highlighter";
    }

    public void setColor(Color color) {
        this.currentColor = color;
    }

    public void setOnDrawingComplete(Consumer<DrawingElement> listener) {
        this.onDrawingComplete = listener;
    }

    private String toHexStringWithAlpha(Color color) {
        return String.format("#%02X%02X%02X%02X",
            (int) (color.getRed() * 255),
            (int) (color.getGreen() * 255),
            (int) (color.getBlue() * 255),
            (int) (color.getOpacity() * 255));
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
        Label label = new Label("Highlighter:");
        label.setStyle("-fx-padding: 0 10 0 10;");
        nodes.add(label);

        // Define transparent neon colors
        List<ColorOption> colors = List.of(
            new ColorOption(Color.rgb(0, 255, 0, 0.3), "Green"),
            new ColorOption(Color.rgb(0, 255, 255, 0.3), "Cyan"),
            new ColorOption(Color.rgb(255, 255, 0, 0.3), "Yellow"),
            new ColorOption(Color.rgb(255, 105, 180, 0.3), "Pink")
        );

        // Create color buttons
        for (ColorOption colorOption : colors) {
            Button colorButton = createColorButton(colorOption.color(), colorOption.name());
            colorButtons.add(colorButton);
            nodes.add(colorButton);
        }

        // Select yellow by default (third button)
        if (colorButtons.size() >= 3) {
            updateButtonSelection(colorButtons.get(2));
        }

        return nodes;
    }

    private Button createColorButton(Color color, String tooltip) {
        Button button = new Button();
        button.setMinSize(24, 24);
        button.setMaxSize(24, 24);
        button.setPrefSize(24, 24);

        // Set background color (with white background to show transparency effect)
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
