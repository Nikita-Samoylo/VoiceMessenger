// client/Main.java
package client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/chat.fxml"));
        primaryStage.setTitle("Voice Chat");
        primaryStage.setScene(new Scene(root, 600, 400));

        // Обработчик закрытия окна
        primaryStage.setOnCloseRequest(e -> {
            System.exit(0);
        });

        primaryStage.show();
    }
}