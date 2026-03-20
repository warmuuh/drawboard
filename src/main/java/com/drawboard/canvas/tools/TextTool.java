package com.drawboard.canvas.tools;

import com.drawboard.domain.elements.TextElement;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Text tool for adding text elements to the canvas.
 * Click to create a new text box at the click location.
 * Provides rich text formatting controls when a text box is selected.
 */
public class TextTool extends AbstractTool {
    private static final Logger log = LoggerFactory.getLogger(TextTool.class);

    private Consumer<TextElement> onTextElementCreated;
    private SelectionTool selectionTool;
    private Runnable onSettingsChanged; // Callback to notify when settings need refresh

    private static final double DEFAULT_WIDTH = 300;
    private static final double DEFAULT_HEIGHT = 100;

    // Rich text formatting controls
    private ToggleButton boldButton;
    private ToggleButton underlineButton;
    private ToggleButton h1Button;
    private ToggleButton h2Button;
    private ToggleButton h3Button;
    private WebView currentWebView;
    private javafx.animation.Timeline formatUpdateTimeline;

    public TextTool(Pane canvasContainer, Pane elementsPane) {
        super(canvasContainer, elementsPane);
        initializeFormatControls();
    }

    public void setSelectionTool(SelectionTool selectionTool) {
        this.selectionTool = selectionTool;
    }

    private void initializeFormatControls() {
        boldButton = new ToggleButton("B");
        boldButton.setStyle("-fx-font-weight: bold;");
        boldButton.setFocusTraversable(false); // Prevent button from taking focus
        boldButton.setOnAction(e -> applyFormat("bold"));

        underlineButton = new ToggleButton("U");
        underlineButton.setStyle("-fx-text-decoration: underline;");
        underlineButton.setFocusTraversable(false); // Prevent button from taking focus
        underlineButton.setOnAction(e -> applyFormat("underline"));

        h1Button = new ToggleButton("H1");
        h1Button.setFocusTraversable(false); // Prevent button from taking focus
        h1Button.setOnAction(e -> applyHeading("h1"));

        h2Button = new ToggleButton("H2");
        h2Button.setFocusTraversable(false); // Prevent button from taking focus
        h2Button.setOnAction(e -> applyHeading("h2"));

        h3Button = new ToggleButton("H3");
        h3Button.setFocusTraversable(false); // Prevent button from taking focus
        h3Button.setOnAction(e -> applyHeading("h3"));

        // Set all buttons to disabled by default (enabled when text node is focused)
        setFormatButtonsEnabled(false);

        // Start timeline to monitor formatting state
        startFormatMonitoring();
    }

    private void startFormatMonitoring() {
        formatUpdateTimeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(
                javafx.util.Duration.millis(100),
                e -> updateFormatButtonStates()
            )
        );
        formatUpdateTimeline.setCycleCount(javafx.animation.Timeline.INDEFINITE);
        formatUpdateTimeline.play();
    }

    private void updateFormatButtonStates() {
        if (currentWebView == null || !currentWebView.isFocused()) {
            return;
        }

        try {
            // Query the current formatting state at cursor position
            Boolean isBold = (Boolean) currentWebView.getEngine().executeScript(
                "document.queryCommandState('bold')"
            );
            Boolean isUnderline = (Boolean) currentWebView.getEngine().executeScript(
                "document.queryCommandState('underline')"
            );

            // Check for heading tags
            String tagName = (String) currentWebView.getEngine().executeScript(
                "(() => { " +
                "  const selection = window.getSelection(); " +
                "  if (selection.rangeCount > 0) { " +
                "    let node = selection.getRangeAt(0).startContainer; " +
                "    while (node && node.nodeType !== 1) node = node.parentNode; " +
                "    while (node && node !== document.body) { " +
                "      if (node.tagName && /^H[1-6]$/.test(node.tagName)) return node.tagName; " +
                "      node = node.parentNode; " +
                "    } " +
                "  } " +
                "  return null; " +
                "})()"
            );

            // Update button states
            boldButton.setSelected(isBold != null && isBold);
            underlineButton.setSelected(isUnderline != null && isUnderline);
            h1Button.setSelected("H1".equals(tagName));
            h2Button.setSelected("H2".equals(tagName));
            h3Button.setSelected("H3".equals(tagName));
        } catch (Exception ex) {
            // Ignore errors during format state check
        }
    }

    private void applyFormat(String command) {
        if (currentWebView == null) {
            return;
        }

        try {
            currentWebView.getEngine().executeScript(
                "document.execCommand('" + command + "', false, null);"
            );
            currentWebView.requestFocus();
        } catch (Exception e) {
            log.error("Failed to apply format: {}", command, e);
        }
    }

    private void applyHeading(String headingTag) {
        if (currentWebView == null) {
            return;
        }

        try {
            // Use formatBlock to apply heading
            currentWebView.getEngine().executeScript(
                "document.execCommand('formatBlock', false, '<" + headingTag + ">');"
            );
            currentWebView.requestFocus();
        } catch (Exception e) {
            log.error("Failed to apply heading: {}", headingTag, e);
        }
    }

    private void setFormatButtonsEnabled(boolean enabled) {
        boldButton.setDisable(!enabled);
        underlineButton.setDisable(!enabled);
        h1Button.setDisable(!enabled);
        h2Button.setDisable(!enabled);
        h3Button.setDisable(!enabled);
    }

    @Override
    public List<Node> getSettingsNodes() {
        List<Node> nodes = new ArrayList<>();

        // Only show formatting controls if a text node is selected
        if (currentWebView != null && currentWebView.isFocused()) {
            nodes.add(new Separator(javafx.geometry.Orientation.VERTICAL));
            nodes.add(boldButton);
            nodes.add(underlineButton);
            nodes.add(new Separator(javafx.geometry.Orientation.VERTICAL));
            nodes.add(h1Button);
            nodes.add(h2Button);
            nodes.add(h3Button);
        }

        return nodes;
    }

    @Override
    protected void handleMousePressed(MouseEvent event) {
        double translateX = elementsPane.getTranslateX();
        double translateY = elementsPane.getTranslateY();
        log.debug("TextTool handleMousePressed at ({}, {}) with translate ({}, {})",
            event.getX(), event.getY(), translateX, translateY);

        // Check if clicking on an existing text element
        javafx.scene.Node clickedNode = findTextNodeAt(event.getX(), event.getY());
        log.debug("findTextNodeAt returned: {}", clickedNode);

        if (clickedNode instanceof javafx.scene.web.WebView webView) {
            // Clicking on existing WebView - select it and focus it for editing

            // Select the node to show blue border and resize handles
            if (selectionTool != null) {
                log.debug("Selecting WebView node via SelectionTool");
                selectionTool.selectNode(webView);
            } else {
                log.warn("SelectionTool is null - cannot select node");
            }

            // Focus the WebView for text editing
            // The focus listener registered in registerWebView() will handle setting currentWebView
            webView.requestFocus();

            // Calculate position relative to WebView
            javafx.geometry.Point2D localPoint = webView.sceneToLocal(event.getSceneX(), event.getSceneY());

            // Fire a new mouse event directly to the WebView
            MouseEvent newEvent = new MouseEvent(
                MouseEvent.MOUSE_PRESSED,
                localPoint.getX(),
                localPoint.getY(),
                event.getScreenX(),
                event.getScreenY(),
                event.getButton(),
                event.getClickCount(),
                event.isShiftDown(),
                event.isControlDown(),
                event.isAltDown(),
                event.isMetaDown(),
                event.isPrimaryButtonDown(),
                event.isMiddleButtonDown(),
                event.isSecondaryButtonDown(),
                event.isSynthesized(),
                event.isPopupTrigger(),
                event.isStillSincePress(),
                event.getPickResult()
            );

            webView.fireEvent(newEvent);

            log.debug("Selected and fired mouse event to WebView at local ({}, {})", localPoint.getX(), localPoint.getY());
            event.consume();
            return;
        }

        // Create new text element at click position with empty content
        // Adjust for canvas translation (already calculated above)
        double adjustedX = event.getX() - translateX;
        double adjustedY = event.getY() - translateY;

        TextElement element = new TextElement(
            UUID.randomUUID().toString(),
            adjustedX,
            adjustedY,
            DEFAULT_WIDTH,
            DEFAULT_HEIGHT,
            "",
            0  // Default z-index for text
        );

        log.debug("Created text element at ({}, {}) with translate ({}, {})",
            adjustedX, adjustedY, translateX, translateY);

        // Notify listener
        if (onTextElementCreated != null) {
            onTextElementCreated.accept(element);
        }

        event.consume();
    }

    private javafx.scene.Node findTextNodeAt(double x, double y) {
        // Find text nodes (WebView) at the given coordinates
        // Account for canvas translation
        double translateX = elementsPane.getTranslateX();
        double translateY = elementsPane.getTranslateY();
        double adjustedX = x - translateX;
        double adjustedY = y - translateY;

        log.debug("findTextNodeAt: click at ({}, {}), adjusted to ({}, {})",
            x, y, adjustedX, adjustedY);

        var children = elementsPane.getChildren();

        for (int i = children.size() - 1; i >= 0; i--) {
            javafx.scene.Node node = children.get(i);

            // Only interested in WebView nodes (text elements)
            if (node instanceof javafx.scene.web.WebView) {
                if (node.contains(node.parentToLocal(adjustedX, adjustedY))) {
                    log.debug("Found WebView at layoutX={}, layoutY={}", node.getLayoutX(), node.getLayoutY());
                    return node;
                }
            }
        }

        log.debug("No WebView found at this position");
        return null;
    }

    @Override
    protected void handleMouseDragged(MouseEvent event) {
        // No tool-specific drag behavior for text tool
    }

    @Override
    protected void handleMouseReleased(MouseEvent event) {
        // No tool-specific release behavior for text tool
    }

    @Override
    public void activate() {
        canvasContainer.setCursor(Cursor.TEXT);
        log.debug("Text tool activated");
    }

    @Override
    public void deactivate() {
        canvasContainer.setCursor(Cursor.DEFAULT);
        setCurrentWebView(null); // Clear current WebView when switching tools
        if (formatUpdateTimeline != null) {
            formatUpdateTimeline.stop();
        }
        log.debug("Text tool deactivated");
    }

    @Override
    public String getName() {
        return "Text";
    }

    public void setOnTextElementCreated(Consumer<TextElement> listener) {
        this.onTextElementCreated = listener;
    }

    /**
     * Set callback for when settings UI needs to be refreshed.
     */
    public void setOnSettingsChanged(Runnable callback) {
        this.onSettingsChanged = callback;
    }

    /**
     * Set the currently focused WebView for formatting operations.
     */
    private void setCurrentWebView(WebView webView) {
        currentWebView = webView;
        setFormatButtonsEnabled(webView != null && webView.isFocused());

        // Notify that toolbar settings need to be refreshed
        if (onSettingsChanged != null) {
            onSettingsChanged.run();
        }

        // Start monitoring format state when WebView is ready
        if (currentWebView != null && formatUpdateTimeline != null &&
            !formatUpdateTimeline.getStatus().equals(javafx.animation.Animation.Status.RUNNING)) {
            formatUpdateTimeline.play();
        }
    }

    /**
     * Public method to register a WebView (called from CanvasManager when WebViews are created).
     * Sets up listeners to track when this WebView becomes focused.
     */
    public void registerWebView(WebView webView) {
        // Set up focus listener to track when this WebView becomes active
        webView.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                setCurrentWebView(webView);
            } else if (currentWebView == webView) {
                // This WebView lost focus - clear it and update toolbar
                currentWebView = null;
                setFormatButtonsEnabled(false);
                if (onSettingsChanged != null) {
                    onSettingsChanged.run();
                }
            }
        });
    }
}
