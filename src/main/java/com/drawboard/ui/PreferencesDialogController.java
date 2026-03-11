package com.drawboard.ui;

import com.drawboard.service.PreferencesService;
import javafx.fxml.FXML;
import javafx.scene.control.ColorPicker;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.function.Consumer;

/**
 * Controller for the preferences dialog.
 */
public class PreferencesDialogController {

    @FXML private ColorPicker backgroundColorPicker;

    private final PreferencesService preferencesService;
    private Stage dialogStage;
    private Consumer<String> onApply;

    public PreferencesDialogController(PreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    @FXML
    public void initialize() {
        // Load current background color
        String currentColor = preferencesService.getBackgroundColor();
        backgroundColorPicker.setValue(Color.web(currentColor));
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setOnApply(Consumer<String> onApply) {
        this.onApply = onApply;
    }

    @FXML
    private void handleApply() {
        // Get selected color as hex string
        Color color = backgroundColorPicker.getValue();
        String hexColor = String.format("#%02X%02X%02X",
            (int) (color.getRed() * 255),
            (int) (color.getGreen() * 255),
            (int) (color.getBlue() * 255)
        );

        // Save to preferences
        preferencesService.saveBackgroundColor(hexColor);

        // Notify listener
        if (onApply != null) {
            onApply.accept(hexColor);
        }

        dialogStage.close();
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }
}
