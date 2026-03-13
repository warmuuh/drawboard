package com.drawboard.ui;

import com.drawboard.service.WebRTCShareService;
import com.drawboard.webrtc.ShareSession;
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
 * Controller for the WebRTC page sharing dialog (PeerJS version).
 * Displays share URL with peer ID and shows connection status.
 * No manual copy-paste of SDP required!
 */
public class ShareDialogController {
    private static final Logger log = LoggerFactory.getLogger(ShareDialogController.class);

    @FXML private TextField shareUrlField;
    @FXML private TextField peerIdField;
    @FXML private Button btnCopyUrl;
    @FXML private Button btnStopSharing;
    @FXML private Button btnClose;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator progressIndicator;

    private final ShareSession shareSession;
    private final WebRTCShareService webrtcService;
    private final String pageId;
    private Stage stage;
    private Timeline statusCheckTimeline;

    public ShareDialogController(ShareSession shareSession, WebRTCShareService webrtcService, String pageId) {
        this.shareSession = shareSession;
        this.webrtcService = webrtcService;
        this.pageId = pageId;
    }

    @FXML
    public void initialize() {
        // Display share URL and peer ID
        shareUrlField.setText(shareSession.shareUrl());
        peerIdField.setText(shareSession.peerId());

        // Start with waiting status
        statusLabel.setText("Waiting for viewer to connect...");
        progressIndicator.setVisible(true);

        // Set up status checking
        setupStatusChecking();
    }

    public void show() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ShareDialog.fxml"));
            loader.setController(this);

            Scene scene = new Scene(loader.load());
            stage = new Stage();
            stage.setTitle("Share Page");
            stage.setScene(scene);
            stage.initModality(Modality.NONE);

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
        content.putString(shareSession.shareUrl());
        clipboard.setContent(content);

        btnCopyUrl.setText("Copied!");
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(2), e -> btnCopyUrl.setText("Copy URL")));
        timeline.play();
    }

    @FXML
    private void handleStopSharing() {
        try {
            webrtcService.stopSharing(pageId);
            statusLabel.setText("Sharing stopped.");
            progressIndicator.setVisible(false);
            btnStopSharing.setDisable(true);

            Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> stage.close()));
            timeline.play();

        } catch (Exception e) {
            log.error("Failed to stop sharing", e);
            showError("Failed to stop sharing: " + e.getMessage());
        }
    }

    @FXML
    private void handleClose() {
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
                statusLabel.setText("Connected! Streaming page in real-time");
                statusLabel.setStyle("-fx-text-fill: green;");
                progressIndicator.setVisible(false);
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

    public Stage getStage() {
        return stage;
    }
}
