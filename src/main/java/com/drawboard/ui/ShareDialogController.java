package com.drawboard.ui;

import com.drawboard.service.WebRTCShareService;
import com.drawboard.webrtc.ShareOffer;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Controller for the WebRTC page sharing dialog.
 * Displays share URL, accepts answer SDP, and shows connection status.
 */
public class ShareDialogController {
    private static final Logger log = LoggerFactory.getLogger(ShareDialogController.class);

    @FXML private TextField shareUrlField;
    @FXML private TextArea answerTextArea;
    @FXML private Button btnCopyUrl;
    @FXML private Button btnConnect;
    @FXML private Button btnStopSharing;
    @FXML private Button btnClose;
    @FXML private Label statusLabel;

    private final ShareOffer shareOffer;
    private final WebRTCShareService webrtcService;
    private final String pageId;
    private Stage stage;
    private Timeline statusCheckTimeline;

    public ShareDialogController(ShareOffer shareOffer, WebRTCShareService webrtcService, String pageId) {
        this.shareOffer = shareOffer;
        this.webrtcService = webrtcService;
        this.pageId = pageId;
    }

    @FXML
    public void initialize() {
        // Display the share URL
        shareUrlField.setText(shareOffer.shareUrl());

        // Set up status checking
        setupStatusChecking();
    }

    /**
     * Show the share dialog.
     */
    public void show() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ShareDialog.fxml"));
            loader.setController(this);

            Scene scene = new Scene(loader.load());
            stage = new Stage();
            stage.setTitle("Share Page");
            stage.setScene(scene);
            stage.initModality(Modality.APPLICATION_MODAL);

            // Stop status checking when dialog closes
            stage.setOnHidden(e -> stopStatusChecking());

            stage.show();
        } catch (IOException e) {
            log.error("Failed to load share dialog", e);
            showError("Failed to open share dialog: " + e.getMessage());
        }
    }

    @FXML
    private void handleCopyUrl() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(shareOffer.shareUrl());
        clipboard.setContent(content);

        // Visual feedback
        btnCopyUrl.setText("Copied!");
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(2), e -> btnCopyUrl.setText("Copy")));
        timeline.play();
    }

    @FXML
    private void handleConnect() {
        String answer = answerTextArea.getText().trim();

        if (answer.isEmpty()) {
            showError("Please paste the answer from the viewer.");
            return;
        }

        try {
            statusLabel.setText("Connecting...");
            btnConnect.setDisable(true);

            // Process answer in background thread
            new Thread(() -> {
                try {
                    webrtcService.processAnswer(pageId, answer);

                    Platform.runLater(() -> {
                        statusLabel.setText("Connected! Page is now being shared.");
                        statusLabel.setStyle("-fx-text-fill: green;");
                        btnStopSharing.setDisable(false);
                        answerTextArea.setDisable(true);
                    });

                } catch (Exception e) {
                    log.error("Failed to process answer", e);
                    Platform.runLater(() -> {
                        statusLabel.setText("Connection failed: " + e.getMessage());
                        statusLabel.setStyle("-fx-text-fill: red;");
                        btnConnect.setDisable(false);
                    });
                }
            }).start();

        } catch (Exception e) {
            log.error("Failed to connect", e);
            showError("Failed to connect: " + e.getMessage());
            btnConnect.setDisable(false);
        }
    }

    @FXML
    private void handleStopSharing() {
        try {
            webrtcService.stopSharing(pageId);
            statusLabel.setText("Sharing stopped.");
            statusLabel.setStyle("-fx-text-fill: gray;");
            btnStopSharing.setDisable(true);

            // Close dialog after a brief delay
            Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> stage.close()));
            timeline.play();

        } catch (Exception e) {
            log.error("Failed to stop sharing", e);
            showError("Failed to stop sharing: " + e.getMessage());
        }
    }

    @FXML
    private void handleClose() {
        // Ask for confirmation if currently sharing
        if (webrtcService.isSharing(pageId)) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Stop Sharing?");
            alert.setHeaderText("You are currently sharing this page.");
            alert.setContentText("Do you want to stop sharing and close this dialog?");

            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    webrtcService.stopSharing(pageId);
                    stage.close();
                }
            });
        } else {
            stage.close();
        }
    }

    private void setupStatusChecking() {
        statusCheckTimeline = new Timeline(new KeyFrame(Duration.millis(500), e -> {
            if (webrtcService.isSharing(pageId)) {
                statusLabel.setText("Connected - sharing page in real-time");
                statusLabel.setStyle("-fx-text-fill: green;");
                btnStopSharing.setDisable(false);
            }
        }));
        statusCheckTimeline.setCycleCount(Animation.INDEFINITE);
        statusCheckTimeline.play();
    }

    private void stopStatusChecking() {
        if (statusCheckTimeline != null) {
            statusCheckTimeline.stop();
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
