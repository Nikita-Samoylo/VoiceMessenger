package client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import java.util.Optional;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import shared.Message;
import shared.SystemMessage;
import shared.TextMessage;
import shared.VoiceMessage;
import java.net.URI;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;

public class ChatController {
    @FXML
    private TextField usernameField;
    @FXML
    private TextField messageField;
    @FXML
    private Button sendButton;
    @FXML
    private ToggleButton recordButton;
    @FXML
    private ListView<Message> messagesList;
    @FXML
    private Label charCountLabel;
    @FXML
    private Label recordingTimeLabel;

    private static final Duration MESSAGE_MAX_AGE = Duration.ofDays(3);
    private Timeline cleanupTimer;
    private WebSocketClient client;
    private AudioRecorder recorder = new AudioRecorder();
    private String currentUser;
    private JSONObject lastVoiceMetadata;
    private Timeline recordingTimer;
    private ObservableList<Message> messageHistory = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        setupUI();
        setupTextListener();
        loadAndCleanHistory();
        connectToServer();
        setupCleanupTimer();
    }

    private void setupUI() {
        messagesList.setItems(messageHistory);
        messagesList.setCellFactory(param -> new MessageListCell());

        recordButton.setText("🎤 Запись");
        recordButton.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white;");
        sendButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");

        charCountLabel.setText(TextMessage.MAX_TEXT_LENGTH + "/" + TextMessage.MAX_TEXT_LENGTH);
        recordingTimeLabel.setText("0/" + (VoiceMessage.MAX_AUDIO_DURATION_MS / 1000) + "с");
        recordingTimeLabel.setVisible(false);
    }

    private void setupTextListener() {
        messageField.textProperty().addListener((observable, oldValue, newValue) -> {
            int remaining = TextMessage.MAX_TEXT_LENGTH - newValue.length();
            charCountLabel.setText(remaining + "/" + TextMessage.MAX_TEXT_LENGTH);
            charCountLabel.setTextFill(remaining < 0 ? Color.RED : Color.BLACK);
        });
    }

    private void loadAndCleanHistory() {
        loadHistory();
        cleanupOldMessages();
        saveHistory();
    }

    private class MessageListCell extends ListCell<Message> {
        @Override
        protected void updateItem(Message message, boolean empty) {
            super.updateItem(message, empty);
            if (empty || message == null) {
                setGraphic(null);
                return;
            }

            boolean isMyMessage = message.getSender().equals(currentUser);

            if (message instanceof TextMessage) {
                setGraphic(createTextMessageBox((TextMessage) message, isMyMessage));
            } else if (message instanceof VoiceMessage) {
                setGraphic(createVoiceMessageBox((VoiceMessage) message, isMyMessage));
            } else if (message instanceof SystemMessage) {
                setGraphic(createSystemMessageBox(((SystemMessage) message).getText()));
            }
        }

        private HBox createTextMessageBox(TextMessage message, boolean isMyMessage) {
            Label senderLabel = new Label((isMyMessage ? "Вы" : message.getSender()) + ":");
            senderLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));

            Label messageLabel = new Label(message.getText());
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
            return container;
        }

        private HBox createVoiceMessageBox(VoiceMessage message, boolean isMyMessage) {
            String duration = formatDuration(message.getDurationMs());
            Label senderLabel = new Label((isMyMessage ? "Вы" : message.getSender()) +
                    " (голосовое, " + duration + "):");
            senderLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));

            Button playButton = new Button("▶ Воспроизвести");
            playButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
            playButton.setOnAction(e -> AudioPlayer.play(message.getAudioData()));

            VBox messageBox = new VBox(5, senderLabel, playButton);
            messageBox.setPadding(new Insets(5, 10, 5, 10));
            messageBox.setStyle(isMyMessage
                    ? "-fx-background-color: #DCF8C6; -fx-background-radius: 10;"
                    : "-fx-background-color: #f0f0f0; -fx-background-radius: 10;");

            HBox container = new HBox(messageBox);
            container.setAlignment(isMyMessage ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            container.setPadding(new Insets(5, 10, 5, 10));
            return container;
        }

        private HBox createSystemMessageBox(String text) {
            Label label = new Label(text);
            label.setFont(Font.font("Arial", 12));
            label.setTextFill(Color.GRAY);

            HBox container = new HBox(label);
            container.setAlignment(Pos.CENTER);
            container.setPadding(new Insets(5, 0, 5, 0));
            return container;
        }
    }

    private void connectToServer() {
        new Thread(() -> {
            try {
                client = new WebSocketClient(new URI("ws://localhost:8080/chat"));
                client.setMessageHandler(new WebSocketClient.MessageHandler() {
                    @Override
                    public void handleText(String message) {
                        try {
                            JSONObject json = new JSONObject(message);
                            if (json.has("error")) {
                                addSystemMessage("Ошибка: " + json.getString("error"));
                            } else if ("voice".equals(json.optString("type"))) {
                                lastVoiceMetadata = json;
                            } else {
                                parseIncomingMessage(message, false);
                            }
                        } catch (JSONException e) {
                            parseIncomingMessage(message, false);
                        }
                    }

                    @Override
                    public void handleAudio(byte[] audioData) {
                        Platform.runLater(() -> {
                            if (lastVoiceMetadata != null) {
                                try {
                                    String sender = lastVoiceMetadata.getString("sender");
                                    VoiceMessage vm = new VoiceMessage(
                                            sender,
                                            audioData,
                                            lastVoiceMetadata.getLong("duration")
                                    );
                                    addMessageToUI(vm, sender.equals(currentUser));
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
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    addSystemMessage("❌ Ошибка подключения: " + e.getMessage());
                    enableControls(false);
                });
            }
        }).start();
    }

    @FXML
    private void handleSendMessage() {
        if (!client.isConnected()) {
            addSystemMessage("Ошибка: Нет подключения к серверу");
            return;
        }

        currentUser = usernameField.getText().isEmpty() ? "Аноним" : usernameField.getText();
        String message = messageField.getText().trim();

        if (message.isEmpty() || message.length() > TextMessage.MAX_TEXT_LENGTH) {
            addSystemMessage(message.isEmpty()
                    ? "Сообщение не может быть пустым"
                    : "Сообщение слишком длинное (максимум " + TextMessage.MAX_TEXT_LENGTH + " символов)");
            return;
        }

        try {
            TextMessage textMessage = new TextMessage(currentUser, message);
            client.sendText(textMessage.toJson());
            addMessageToUI(textMessage, true);
            messageField.clear();
        } catch (Exception e) {
            addSystemMessage("Ошибка отправки сообщения: " + e.getMessage());
        }
    }

    @FXML
    private void handleRecord() {
        if (recordButton.isSelected()) {
            startRecording();
        } else {
            stopRecordingAndSend();
        }
    }

    private void startRecording() {
        recorder.startRecording();
        recordButton.setText("⏹ Остановить");
        recordButton.setStyle("-fx-background-color: #ff0000; -fx-text-fill: white;");
        recordingTimeLabel.setVisible(true);

        recordingTimer = new Timeline(new KeyFrame(
                javafx.util.Duration.seconds(1),
                event -> updateRecordingTimer()
        ));
        recordingTimer.setCycleCount(Animation.INDEFINITE);
        recordingTimer.play();
    }

    private void updateRecordingTimer() {
        if (recorder.isRecording()) {
            long elapsedSeconds = Duration.between(
                    recorder.getRecordingStartTime(),
                    Instant.now()
            ).getSeconds();

            recordingTimeLabel.setText(elapsedSeconds + "/" +
                    (VoiceMessage.MAX_AUDIO_DURATION_MS / 1000) + "с");

            if (elapsedSeconds >= VoiceMessage.MAX_AUDIO_DURATION_MS / 1000 - 5) {
                recordingTimeLabel.setTextFill(Color.RED);
            }

            if (elapsedSeconds >= VoiceMessage.MAX_AUDIO_DURATION_MS / 1000-1) {
                Platform.runLater(() -> {
                    recordButton.setSelected(false);
                    stopRecordingAndSend();
                });
            }
        }
    }

    private void stopRecordingAndSend() {
        if (recordingTimer != null) recordingTimer.stop();
        recordingTimeLabel.setVisible(false);
        recordingTimeLabel.setTextFill(Color.BLACK);

        AudioRecorder.RecordingResult result = recorder.stopRecording();
        recordButton.setText("🎤 Запись");
        recordButton.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white;");

        if (result.getAudioData() == null || result.getAudioData().length == 0) {
            addSystemMessage("Не удалось записать аудио");
            return;
        }

        if (result.getDurationMs() > VoiceMessage.MAX_AUDIO_DURATION_MS ||
                result.getAudioData().length > VoiceMessage.MAX_AUDIO_SIZE_BYTES) {
            addSystemMessage("Голосовое сообщение слишком длинное (максимум " +
                    VoiceMessage.MAX_AUDIO_DURATION_MS / 1000 + " секунд)");
            return;
        }

        currentUser = usernameField.getText().isEmpty() ? "Аноним" : usernameField.getText();
        try {
            VoiceMessage vm = new VoiceMessage(currentUser,
                    result.getAudioData(),
                    result.getDurationMs()
            );
            client.sendText(vm.toJson());
            client.sendAudio(result.getAudioData());
            addMessageToUI(vm, true);
        } catch (Exception e) {
            addSystemMessage("Ошибка отправки голосового сообщения");
        }
    }

    private void addMessageToUI(Message message, boolean isMyMessage) {
        Platform.runLater(() -> {
            messageHistory.add(message);
            scrollToBottom();
        });
    }

    private void addSystemMessage(String text) {
        addMessageToUI(new SystemMessage(text), false);
    }

    private void parseIncomingMessage(String message, boolean isMyMessage) {
        try {
            JSONObject json = new JSONObject(message);
            if ("text".equals(json.getString("type"))) {
                TextMessage tm = new TextMessage(
                        json.getString("sender"),
                        json.getString("content")
                );
                tm.setTimestamp(Instant.parse(json.getString("timestamp")));
                addMessageToUI(tm, isMyMessage);
            }
        } catch (JSONException e) {
            addSystemMessage("Неверный формат сообщения");
        }
    }

    private void scrollToBottom() {
        Platform.runLater(() ->
                messagesList.scrollTo(messagesList.getItems().size() - 1));
    }

    private String formatDuration(long durationMs) {
        long seconds = (durationMs / 1000) % 60;
        long minutes = (durationMs / (1000 * 60)) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private void enableControls(boolean enabled) {
        recordButton.setDisable(!enabled);
        sendButton.setDisable(!enabled);
    }

    private void setupCleanupTimer() {
        cleanupTimer = new Timeline(
                new KeyFrame(javafx.util.Duration.hours(1), event -> cleanupOldMessages())
        );
        cleanupTimer.setCycleCount(Animation.INDEFINITE);
        cleanupTimer.play();
    }

    private void cleanupOldMessages() {
        messageHistory.removeIf(message ->
                message.getTimestamp().plus(MESSAGE_MAX_AGE).isBefore(Instant.now())
        );
    }

    public void saveHistory() {
        try {
            JSONArray history = new JSONArray();
            Instant now = Instant.now();

            for (Message msg : messageHistory) {
                if (msg instanceof SystemMessage) continue;
                if (msg.getTimestamp().plus(MESSAGE_MAX_AGE).isBefore(now)) continue;

                JSONObject json = new JSONObject();
                if (msg instanceof TextMessage) {
                    TextMessage tm = (TextMessage) msg;
                    json.put("type", "text")
                            .put("sender", tm.getSender())
                            .put("content", tm.getText())
                            .put("timestamp", tm.getTimestamp().toString());
                } else if (msg instanceof VoiceMessage) {
                    VoiceMessage vm = (VoiceMessage) msg;
                    json.put("type", "voice")
                            .put("sender", vm.getSender())
                            .put("duration", vm.getDurationMs())
                            .put("data", Base64.getEncoder().encodeToString(vm.getAudioData()))
                            .put("timestamp", vm.getTimestamp().toString());
                }
                history.put(json);
            }

            Files.createDirectories(Paths.get("history"));
            Files.write(Paths.get("history/chat_history.json"), history.toString(2).getBytes());
        } catch (Exception e) {
            System.err.println("Ошибка сохранения истории: " + e.getMessage());
        }
    }

    private void loadHistory() {
        try {
            Path path = Paths.get("history/chat_history.json");
            if (!Files.exists(path)) return;

            JSONArray history = new JSONArray(new String(Files.readAllBytes(path)));
            Instant now = Instant.now();
            messageHistory.clear();

            for (int i = 0; i < history.length(); i++) {
                JSONObject msg = history.getJSONObject(i);
                Instant timestamp = Instant.parse(msg.getString("timestamp"));

                if (timestamp.plus(MESSAGE_MAX_AGE).isBefore(now)) continue;

                if ("text".equals(msg.getString("type"))) {
                    TextMessage tm = new TextMessage(
                            msg.getString("sender"),
                            msg.getString("content")
                    );
                    tm.setTimestamp(timestamp);
                    messageHistory.add(tm);
                } else if ("voice".equals(msg.getString("type"))) {
                    VoiceMessage vm = new VoiceMessage(
                            msg.getString("sender"),
                            Base64.getDecoder().decode(msg.getString("data")),
                            msg.getLong("duration")
                    );
                    vm.setTimestamp(timestamp);
                    messageHistory.add(vm);
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка загрузки истории: " + e.getMessage());
        }

    }

    @FXML
    private void handleClearHistory() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Подтверждение удаления");
        alert.setHeaderText("Удаление всей истории сообщений");
        alert.setContentText("Вы уверены, что хотите безвозвратно удалить всю историю чата?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                // Очистка текущей истории
                messageHistory.clear();

                // Удаление файла истории
                Path historyFile = Paths.get("history/chat_history.json");
                if (Files.exists(historyFile)) {
                    Files.delete(historyFile);
                    addSystemMessage("История сообщений успешно удалена");
                }
                
            } catch (IOException e) {
                System.err.println("Ошибка удаления истории: " + e.getMessage());
                addSystemMessage("Ошибка при удалении истории: " + e.getMessage());
            }
        }
    }
}