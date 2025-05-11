package client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.json.JSONObject;
import org.json.JSONException;
import shared.TextMessage;
import shared.VoiceMessage;
import java.net.URI;

public class ChatController {
    @FXML private TextField usernameField;
    @FXML private TextField messageField;
    @FXML private Button sendButton;
    @FXML private ToggleButton recordButton;
    @FXML private ListView<HBox> messagesList;
    @FXML private VBox mainContainer;

    private WebSocketClient client;
    private AudioRecorder recorder = new AudioRecorder();
    private String currentUser;
    private JSONObject lastVoiceMetadata;

    @FXML
    private void initialize() {
        System.out.println("Инициализация контроллера...");

        // Настройка UI
        recordButton.setText("🎤 Запись");
        recordButton.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white;");
        sendButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");

        messagesList.setCellFactory(param -> new ListCell<HBox>() {
            @Override
            protected void updateItem(HBox item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : item);
            }
        });

        connectToServer();
    }

    private void connectToServer() {
        new Thread(() -> {
            try {
                System.out.println("Попытка подключения к WebSocket...");
                client = new WebSocketClient(new URI("ws://localhost:8080/chat"));

                client.setMessageHandler(new WebSocketClient.MessageHandler() {
                    @Override
                    public void handleText(String message) {
                        try {
                            JSONObject json = new JSONObject(message);
                            if ("voice".equals(json.optString("type"))) {
                                lastVoiceMetadata = json;
                            } else {
                                Platform.runLater(() -> parseIncomingMessage(message, false));
                            }
                        } catch (JSONException e) {
                            Platform.runLater(() -> parseIncomingMessage(message, false));
                        }
                    }

                    @Override
                    public void handleAudio(byte[] audioData) {
                        Platform.runLater(() -> {
                            if (lastVoiceMetadata != null) {
                                try {
                                    String sender = lastVoiceMetadata.getString("sender");
                                    long duration = lastVoiceMetadata.getLong("duration");
                                    addVoiceMessage(audioData, duration, sender, sender.equals(currentUser));
                                } catch (JSONException e) {
                                    addSystemMessage("Ошибка обработки голосового сообщения");
                                }
                                lastVoiceMetadata = null;
                            }
                        });
                    }
                });

                Platform.runLater(() -> {
                    addSystemMessage("✅ Подключено к серверу!");
                    enableControls(true);
                    System.out.println("Успешное подключение");
                });
            } catch (Exception e) {
                System.err.println("Ошибка подключения: " + e.getMessage());
                Platform.runLater(() -> {
                    addSystemMessage("❌ Ошибка подключения: " + e.getMessage());
                    enableControls(false);
                });
            }
        }).start();
    }

    private void addVoiceMessage(byte[] audioData, long durationMs, String sender, boolean isMyMessage) {
        String displayName = isMyMessage ? "Вы" : sender;
        String duration = formatDuration(durationMs);

        Label senderLabel = new Label(displayName + " (голосовое, " + duration + "):");
        senderLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        Button playButton = new Button("▶ Воспроизвести");
        playButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        playButton.setOnAction(e -> {
            System.out.println("Playing audio (" + audioData.length + " bytes)");
            AudioPlayer.play(audioData);
        });

        VBox messageBox = new VBox(5, senderLabel, playButton);
        messageBox.setPadding(new Insets(5, 10, 5, 10));
        messageBox.setStyle(isMyMessage
                ? "-fx-background-color: #DCF8C6; -fx-background-radius: 10;"
                : "-fx-background-color: #f0f0f0; -fx-background-radius: 10;");

        HBox container = new HBox(messageBox);
        container.setAlignment(isMyMessage ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        container.setPadding(new Insets(5, 10, 5, 10));

        messagesList.getItems().add(container);
        scrollToBottom();
    }

    @FXML
    private void handleRecord() {
        if (recordButton.isSelected()) {
            startRecording();
        } else {
            AudioRecorder.RecordingResult result = recorder.stopRecording();
            recordButton.setText("🎤 Запись");
            recordButton.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white;");

            if (result.getAudioData() != null && result.getAudioData().length > 0) {
                currentUser = usernameField.getText().isEmpty() ? "Аноним" : usernameField.getText();
                VoiceMessage voiceMessage = new VoiceMessage(currentUser, result.getAudioData(), result.getDurationMs());

                try {
                    client.sendText(voiceMessage.toJson());
                    client.sendAudio(result.getAudioData());
                    addVoiceMessage(result.getAudioData(), result.getDurationMs(), currentUser, true);
                } catch (Exception e) {
                    System.err.println("Error sending audio message:");
                    e.printStackTrace();
                    addSystemMessage("Ошибка отправки голосового сообщения");
                }
            }
        }
    }

    // Остальные методы остаются без изменений
    private void enableControls(boolean enabled) {
        recordButton.setDisable(!enabled);
        sendButton.setDisable(!enabled);
    }

    private void addSystemMessage(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("Arial", 12));
        label.setTextFill(Color.GRAY);

        HBox container = new HBox(label);
        container.setAlignment(Pos.CENTER);
        container.setPadding(new Insets(5, 0, 5, 0));
        messagesList.getItems().add(container);
        scrollToBottom();
    }

    private void parseIncomingMessage(String message, boolean isMyMessage) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.getString("type");

            if ("text".equals(type)) {
                addTextMessage(json, isMyMessage);
            }
        } catch (JSONException e) {
            addSystemMessage("Неверный формат сообщения");
        }
    }

    private void addTextMessage(JSONObject json, boolean isMyMessage) throws JSONException {
        String sender = json.getString("sender");
        String content = json.getString("content");

        Label senderLabel = new Label(sender + ":");
        senderLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        Label messageLabel = new Label(content);
        messageLabel.setFont(Font.font("Arial", 14));
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(300);
        messageLabel.setPadding(new Insets(5, 10, 5, 10));

        VBox messageBox = new VBox(2, senderLabel, messageLabel);
        messageBox.setPadding(new Insets(5));
        messageBox.setStyle(isMyMessage
                ? "-fx-background-color: #DCF8C6; -fx-background-radius: 10;"
                : "-fx-background-color: #ECECEC; -fx-background-radius: 10;");

        HBox container = new HBox(messageBox);
        container.setAlignment(isMyMessage ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        container.setPadding(new Insets(5, 10, 5, 10));

        messagesList.getItems().add(container);
        scrollToBottom();
    }

    private String formatDuration(long durationMs) {
        long seconds = (durationMs / 1000) % 60;
        long minutes = (durationMs / (1000 * 60)) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private void scrollToBottom() {
        messagesList.scrollTo(messagesList.getItems().size() - 1);
    }

    @FXML
    private void handleSendMessage() {
        if (!client.isConnected()) {
            addSystemMessage("Ошибка: Нет подключения к серверу");
            return;
        }

        currentUser = usernameField.getText().isEmpty() ? "Аноним" : usernameField.getText();
        String message = messageField.getText();

        if (!message.isEmpty()) {
            TextMessage textMessage = new TextMessage(currentUser, message);
            client.sendText(textMessage.toJson());
            parseIncomingMessage(textMessage.toJson(), true);
            messageField.clear();
        }
    }

    private void startRecording() {
        recorder.startRecording();
        recordButton.setText("⏹ Остановить");
        recordButton.setStyle("-fx-background-color: #ff0000; -fx-text-fill: white;");
    }
}