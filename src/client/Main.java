package client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    private ChatController chatController;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Загружаем FXML с сохранением ссылки на контроллер
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/chat.fxml"));
        Parent root = loader.load();
        chatController = loader.getController();

        Scene scene = new Scene(root, 600, 500);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        primaryStage.setTitle("Голосовой чат");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(400);
        primaryStage.setMinHeight(400);

        // Обработчик закрытия окна с сохранением истории
        primaryStage.setOnCloseRequest(e -> {
            if(chatController != null) {
                chatController.saveHistory();
            }
            System.exit(0);
        });

        primaryStage.show();
    }
}