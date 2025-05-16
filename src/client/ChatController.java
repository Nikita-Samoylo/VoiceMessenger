package client;

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
import shared.TextMessage;
import shared.VoiceMessage;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;

public class ChatController {
    @FXML private TextField usernameField;
    @FXML private TextField messageField;
    @FXML private Button sendButton;
    @FXML private ToggleButton recordButton;
    @FXML private ListView<HBox> messagesList;
    @FXML private VBox mainContainer;
    @FXML private Label charCountLabel;
    @FXML private Label recordingTimeLabel;

    private WebSocketClient client;
    private AudioRecorder recorder = new AudioRecorder();
    private String currentUser;
    private JSONObject lastVoiceMetadata;
    private Timeline recordingTimer;
    private ObservableList<Message> messageHistory = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        System.out.println("Инициализация контроллера...");

        // Настройка UI
        recordButton.setText("🎤 Запись");
        recordButton.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white;");
        sendButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");

        // Инициализация счетчиков
        charCountLabel.setText(TextMessage.MAX_TEXT_LENGTH + "/" + TextMessage.MAX_TEXT_LENGTH);
        recordingTimeLabel.setText("0/" + (VoiceMessage.MAX_AUDIO_DURATION_MS/1000) + "с");
        recordingTimeLabel.setVisible(false);

        // Слушатель изменения текста
        messageField.textProperty().addListener((observable, oldValue, newValue) -> {
            int remaining = TextMessage.MAX_TEXT_LENGTH - newValue.length();
            charCountLabel.setText(remaining + "/" + TextMessage.MAX_TEXT_LENGTH);
            charCountLabel.setTextFill(remaining < 0 ? Color.RED : Color.BLACK);
        });

        messagesList.setCellFactory(param -> new ListCell<HBox>() {
            @Override
            protected void updateItem(HBox item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : item);
            }
        });

        connectToServer();
        loadHistory();
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
                            if (json.has("error")) {
                                Platform.runLater(() ->
                                        addSystemMessage("Ошибка: " + json.getString("error")));
                            } else if ("voice".equals(json.optString("type"))) {
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
                                    VoiceMessage vm = new VoiceMessage(sender, audioData, duration);
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

    @FXML
    private void handleSendMessage() {
        if (!client.isConnected()) {
            addSystemMessage("Ошибка: Нет подключения к серверу");
            return;
        }

        currentUser = usernameField.getText().isEmpty() ? "Аноним" : usernameField.getText();
        String message = messageField.getText().trim();

        if (message.isEmpty()) {
            addSystemMessage("Сообщение не может быть пустым");
            return;
        }

        if (message.length() > TextMessage.MAX_TEXT_LENGTH) {
            addSystemMessage("Ошибка: Сообщение слишком длинное (максимум " +
                    TextMessage.MAX_TEXT_LENGTH + " символов)");
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

        recordingTimer = new Timeline(
                new KeyFrame(javafx.util.Duration.seconds(1), event -> {
                    if (recorder.isRecording()) {
                        long elapsedSeconds = Duration.between(
                                recorder.getRecordingStartTime(),
                                Instant.now()
                        ).getSeconds();

                        recordingTimeLabel.setText(
                                elapsedSeconds + "/" +
                                        (VoiceMessage.MAX_AUDIO_DURATION_MS/1000) + "с"
                        );

                        if (elapsedSeconds >= VoiceMessage.MAX_AUDIO_DURATION_MS/1000 - 5) {
                            recordingTimeLabel.setTextFill(Color.RED);
                        }

                        if (elapsedSeconds >= VoiceMessage.MAX_AUDIO_DURATION_MS/1000) {
                            Platform.runLater(() -> {
                                recordButton.setSelected(false);
                                stopRecordingAndSend();
                            });
                        }
                    }
                })
        );
        recordingTimer.setCycleCount(Animation.INDEFINITE);
        recordingTimer.play();
    }

    private void stopRecordingAndSend() {
        if (recordingTimer != null) {
            recordingTimer.stop();
        }
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
            addSystemMessage("Ошибка: Голосовое сообщение слишком длинное (максимум " +
                    VoiceMessage.MAX_AUDIO_DURATION_MS/1000 + " секунд)");
            return;
        }

        currentUser = usernameField.getText().isEmpty() ? "Аноним" : usernameField.getText();

        try {
            VoiceMessage voiceMessage = new VoiceMessage(currentUser, result.getAudioData(), result.getDurationMs());
            client.sendText(voiceMessage.toJson());
            client.sendAudio(result.getAudioData());
            addMessageToUI(voiceMessage, true);
        } catch (Exception e) {
            System.err.println("Error sending audio message:");
            e.printStackTrace();
            addSystemMessage("Ошибка отправки голосового сообщения");
        }
    }

    private void addMessageToUI(Message message, boolean isMyMessage) {
        messageHistory.add(message);
        if (message instanceof TextMessage) {
            showTextMessage((TextMessage) message, isMyMessage);
        } else if (message instanceof VoiceMessage) {
            showVoiceMessage((VoiceMessage) message, isMyMessage);
        }
    }

    private void showTextMessage(TextMessage message, boolean isMyMessage) {
        String sender = isMyMessage ? "Вы" : message.getSender();
        String content = message.getText();

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

        Platform.runLater(() -> {
            messagesList.getItems().add(container);
            scrollToBottom();
        });
    }

    private void showVoiceMessage(VoiceMessage message, boolean isMyMessage) {
        String displayName = isMyMessage ? "Вы" : message.getSender();
        String duration = formatDuration(message.getDurationMs());

        Label senderLabel = new Label(displayName + " (голосовое, " + duration + "):");
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

        Platform.runLater(() -> {
            messagesList.getItems().add(container);
            scrollToBottom();
        });
    }

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

        Platform.runLater(() -> {
            messagesList.getItems().add(container);
            scrollToBottom();
        });
    }

    private void parseIncomingMessage(String message, boolean isMyMessage) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.getString("type");

            if ("text".equals(type)) {
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

    private String formatDuration(long durationMs) {
        long seconds = (durationMs / 1000) % 60;
        long minutes = (durationMs / (1000 * 60)) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private void scrollToBottom() {
        Platform.runLater(() ->
                messagesList.scrollTo(messagesList.getItems().size() - 1));
    }

    public void saveHistory() {
        try {
            JSONArray history = new JSONArray();
            for (Message msg : messageHistory) {
                JSONObject json = new JSONObject();

                if (msg instanceof TextMessage) {
                    TextMessage tm = (TextMessage) msg;
                    json.put("type", "text");
                    json.put("sender", tm.getSender());
                    json.put("content", tm.getText());
                    json.put("timestamp", tm.getTimestamp().toString());
                }
                else if (msg instanceof VoiceMessage) {
                    VoiceMessage vm = (VoiceMessage) msg;
                    json.put("type", "voice");
                    json.put("sender", vm.getSender());
                    json.put("duration", vm.getDurationMs());
                    json.put("data", Base64.getEncoder().encodeToString(vm.getAudioData()));
                    json.put("timestamp", vm.getTimestamp().toString());
                }
                history.put(json);
            }

            // Создаем папку history если её нет
            Path dir = Paths.get("history");
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            // Сохраняем в папку проекта
            Path path = dir.resolve("chat_history.json");
            Files.write(path, history.toString(2).getBytes());
        } catch (Exception e) {
            System.err.println("Ошибка сохранения истории: " + e.getMessage());
        }
    }

    private void loadHistory() {
        try {
            // Путь к файлу в папке проекта
            Path path = Paths.get("history/chat_history.json");
            if (!Files.exists(path)) return;

            String content = new String(Files.readAllBytes(path));
            JSONArray history = new JSONArray(content);

            for (int i = 0; i < history.length(); i++) {
                JSONObject msg = history.getJSONObject(i);
                if ("text".equals(msg.getString("type"))) {
                    TextMessage tm = new TextMessage(
                            msg.getString("sender"),
                            msg.getString("content")
                    );
                    tm.setTimestamp(Instant.parse(msg.getString("timestamp")));
                    addMessageToUI(tm, tm.getSender().equals(currentUser));
                }
                else if ("voice".equals(msg.getString("type"))) {
                    VoiceMessage vm = new VoiceMessage(
                            msg.getString("sender"),
                            Base64.getDecoder().decode(msg.getString("data")),
                            msg.getLong("duration")
                    );
                    vm.setTimestamp(Instant.parse(msg.getString("timestamp")));
                    addMessageToUI(vm, vm.getSender().equals(currentUser));
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка загрузки истории: " + e.getMessage());
        }
    }
}