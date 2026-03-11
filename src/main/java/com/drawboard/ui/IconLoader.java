package com.drawboard.ui;

import javafx.scene.Node;
import org.girod.javafx.svgimage.SVGImage;
import org.girod.javafx.svgimage.SVGLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

/**
 * Utility class for loading SVG icons from resources.
 */
public class IconLoader {
    private static final Logger log = LoggerFactory.getLogger(IconLoader.class);
    private static final String ICON_BASE_PATH = "/icons/";

    /**
     * Load an SVG icon from resources.
     *
     * @param iconName the name of the icon file (without .svg extension)
     * @return the loaded SVG as a JavaFX Node, or null if loading fails
     */
    public static Node loadIcon(String iconName) {
        return loadIcon(iconName, -1, -1);
    }

    /**
     * Load an SVG icon from resources with specific size.
     *
     * @param iconName the name of the icon file (without .svg extension)
     * @param width desired width in pixels, or -1 to use original size
     * @param height desired height in pixels, or -1 to use original size
     * @return the loaded SVG as a JavaFX Node, or null if loading fails
     */
    public static Node loadIcon(String iconName, double width, double height) {
        String iconPath = ICON_BASE_PATH + iconName + ".svg";
        URL iconUrl = IconLoader.class.getResource(iconPath);

        if (iconUrl == null) {
            log.warn("Icon not found: {}", iconPath);
            return null;
        }

        try {
            SVGImage svgImage = SVGLoader.load(iconUrl);
            if (svgImage != null) {
                // Apply scaling if dimensions are specified
                if (width > 0 && height > 0) {
                    double scaleX = width / svgImage.getBoundsInLocal().getWidth();
                    double scaleY = height / svgImage.getBoundsInLocal().getHeight();
                    svgImage.setScaleX(scaleX);
                    svgImage.setScaleY(scaleY);
                }
                return svgImage;
            }
        } catch (Exception e) {
            log.error("Failed to load icon: {}", iconPath, e);
        }

        return null;
    }
}
