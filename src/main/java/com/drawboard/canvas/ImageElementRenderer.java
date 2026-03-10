package com.drawboard.canvas;

import com.drawboard.domain.elements.ImageElement;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;

/**
 * Renders image elements using ImageView.
 * Images are loaded from byte arrays provided by the storage service.
 */
public class ImageElementRenderer {
    private static final Logger log = LoggerFactory.getLogger(ImageElementRenderer.class);

    private byte[] imageDataProvider;  // Will be set by canvas manager when needed

    public Node render(ImageElement element) {
        // For now, create a placeholder rectangle
        // In Phase 6, we'll integrate with storage service to load actual images
        Rectangle placeholder = new Rectangle(
            element.width() > 0 ? element.width() : 200,
            element.height() > 0 ? element.height() : 150
        );
        placeholder.setLayoutX(element.x());
        placeholder.setLayoutY(element.y());
        placeholder.setFill(Color.LIGHTGRAY);
        placeholder.setStroke(Color.DARKGRAY);
        placeholder.setStrokeWidth(1);

        return placeholder;
    }

    /**
     * Render an image element with actual image data.
     */
    public Node renderWithData(ImageElement element, byte[] imageData) {
        try {
            Image image = new Image(new ByteArrayInputStream(imageData));
            ImageView imageView = new ImageView(image);

            imageView.setLayoutX(element.x());
            imageView.setLayoutY(element.y());

            // Use specified dimensions or original image size
            if (element.width() > 0 && element.height() > 0) {
                imageView.setFitWidth(element.width());
                imageView.setFitHeight(element.height());
            } else {
                imageView.setFitWidth(image.getWidth());
                imageView.setFitHeight(image.getHeight());
            }

            imageView.setPreserveRatio(true);

            return imageView;
        } catch (Exception e) {
            log.error("Failed to load image: {}", element.filename(), e);
            return render(element);  // Fall back to placeholder
        }
    }
}
