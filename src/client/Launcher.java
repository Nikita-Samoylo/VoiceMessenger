package client;

import server.ServerMain;
import javafx.application.Application;

public class Launcher {
    public static void main(String[] args) {
        // Запускаем сервер в отдельном потоке
        Thread serverThread = new Thread(() -> {
            ServerMain.main(new String[]{});
        });
        serverThread.setDaemon(true); // Делаем поток демоном
        serverThread.start();

        // Даем серверу больше времени на запуск (5 секунд)
        try {
            for (int i = 0; i < 5; i++) {
                System.out.println("Waiting for server to start..." + (5 - i) + "s");
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Запускаем клиент
        Application.launch(Main.class, args);
    }
}