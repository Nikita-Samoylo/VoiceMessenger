// client/ChatController.java
package client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import shared.TextMessage;
import shared.VoiceMessage;

import java.net.URI;

public class ChatController {
    @FXML private TextField usernameField;
    @FXML private TextField messageField;
    @FXML private Button sendButton;
    @FXML private ToggleButton recordButton;
    @FXML private ListView<String> messagesList;
    @FXML private VBox mainContainer;

    private WebSocketClient client;
    private AudioRecorder recorder = new AudioRecorder();
    private String currentUser;

    @FXML
    private void initialize() {
        recordButton.setText("🎤");
        messagesList.getItems().add("Connecting to server...");

        new Thread(() -> {
            try {
                client = new WebSocketClient(new URI("ws://localhost:8080/chat"));
                client.setMessageHandler(new WebSocketClient.MessageHandler() {
                    @Override
                    public void handleText(String message) {
                        Platform.runLater(() ->
                                messagesList.getItems().add("Received: " + message));
                    }

                    @Override
                    public void handleAudio(byte[] audioData) {
                        Platform.runLater(() -> {
                            messagesList.getItems().add("Voice message received");
                            AudioPlayer.play(audioData);
                        });
                    }
                });

                Platform.runLater(() -> {
                    messagesList.getItems().add("✅ Connected to server!");
                    recordButton.setDisable(false);
                    sendButton.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    messagesList.getItems().add("❌ Connection failed: " + e.getMessage());
                    recordButton.setDisable(true);
                    sendButton.setDisable(true);
                });
            }
        }).start();
    }

    @FXML
    private void handleSendMessage() {
        if (!client.isConnected()) {
            messagesList.getItems().add("Error: Not connected to server");
            return;
        }
        currentUser = usernameField.getText().isEmpty() ? "Anonymous" : usernameField.getText();
        String message = messageField.getText();

        if (!message.isEmpty()) {
            TextMessage textMessage = new TextMessage(currentUser, message);
            client.sendText(textMessage.toJson());
            messagesList.getItems().add("You: " + message);
            messageField.clear();
        }
    }

    @FXML
    private void handleRecord() {
        if (recordButton.isSelected()) {
            recorder.startRecording();
            recordButton.setText("⏹");
        } else {
            byte[] audioData = recorder.stopRecording();
            recordButton.setText("🎤");

            if (audioData != null && audioData.length > 0) {
                currentUser = usernameField.getText().isEmpty() ? "Anonymous" : usernameField.getText();
                VoiceMessage voiceMessage = new VoiceMessage(currentUser, audioData);
                client.sendText(voiceMessage.toJson());
                client.sendAudio(audioData);
                messagesList.getItems().add("You sent voice message");
            }
        }
    }
}