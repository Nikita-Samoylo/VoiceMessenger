package server;

import org.glassfish.tyrus.server.Server;
import java.util.Scanner;

public class ServerMain {
    public static void main(String[] args) {
        System.setProperty("org.glassfish.tyrus.server.ServerContainer",
                "org.glassfish.tyrus.container.grizzly.server.GrizzlyServerContainer");

        Server server = new Server("localhost", 8080, "/", null, ChatEndpoint.class);

        try {
            server.start();
            System.out.println("✅ Server running at ws://localhost:8080/chat");
            System.out.println("⏹ Press Enter to stop...");

            //Для отладки
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down server...");
                server.stop();
            }));

            new Scanner(System.in).nextLine();
        } catch (Exception e) {
            System.err.println("❌ Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            server.stop();
            System.out.println("🛑 Server stopped");
        }
    }
}