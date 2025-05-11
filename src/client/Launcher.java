package client;

import server.ServerMain;
import javafx.application.Application;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class Launcher {
    private static final String SERVER_URL = "http://localhost:8080";
    private static final int SERVER_PORT = 8080;
    private static final int CONNECTION_TIMEOUT_SECONDS = 2;

    public static void main(String[] args) {
        if (!isServerRunning()) {
            startServerInBackground();
        }
        waitForServerStart();
        Application.launch(Main.class, args);
    }

    private static boolean isServerRunning() {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SERVER_URL))
                .timeout(Duration.ofSeconds(CONNECTION_TIMEOUT_SECONDS))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return true;
        } catch (Exception e) {
            System.out.println("Сервер не запущен, будет запущен автоматически");
            return false;
        }
    }

    private static void startServerInBackground() {
        Thread serverThread = new Thread(() -> {
            ServerMain.main(new String[]{});
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private static void waitForServerStart() {
        int attempts = 0;
        int maxAttempts = 10;
        boolean serverReady = false;

        System.out.println("Ожидание запуска сервера...");
        while (!serverReady && attempts < maxAttempts) {
            try {
                Thread.sleep(1000);
                serverReady = isServerRunning();
                System.out.println("Попытка " + (attempts + 1) + ": " +
                        (serverReady ? "Сервер запущен!" : "Сервер еще не готов"));
                attempts++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (!serverReady) {
            System.err.println("Не удалось подключиться к серверу после " + maxAttempts + " попыток");
            System.exit(1);
        }
    }
}