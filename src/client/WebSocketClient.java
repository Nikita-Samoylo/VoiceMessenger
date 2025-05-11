package client;

import org.glassfish.tyrus.client.ClientManager;
import jakarta.websocket.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@ClientEndpoint
public class WebSocketClient {
    private Session session;
    private MessageHandler messageHandler;
    private final CountDownLatch latch = new CountDownLatch(1);

    public WebSocketClient(URI endpointURI) {
        try {
            ClientManager client = ClientManager.createClient();
            client.connectToServer(this, endpointURI);

            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Connection timeout");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to WebSocket server", e);
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        latch.countDown();
    }

    @OnMessage
    public void onTextMessage(String message) {
        if (messageHandler != null) {
            messageHandler.handleText(message);
        }
    }

    @OnMessage
    public void onBinaryMessage(byte[] data) {
        if (messageHandler != null) {
            messageHandler.handleAudio(data);
        }
    }

    @OnError
    public void onError(Session session, Throwable thr) {
        System.err.println("WebSocket error: " + thr.getMessage());
    }

    @OnClose
    public void onClose(Session session) {
        this.session = null;
    }

    public void sendText(String message) {
        if (isConnected()) {
            session.getAsyncRemote().sendText(message);
        } else {
            throw new IllegalStateException("WebSocket is not connected");
        }
    }

    public void sendAudio(byte[] audioData) {
        if (isConnected()) {
            session.getAsyncRemote().sendBinary(ByteBuffer.wrap(audioData));
        } else {
            throw new IllegalStateException("WebSocket is not connected");
        }
    }

    public void setMessageHandler(MessageHandler msgHandler) {
        this.messageHandler = msgHandler;
    }

    public boolean isConnected() {
        return session != null && session.isOpen();
    }

    public interface MessageHandler {
        void handleText(String message);
        void handleAudio(byte[] audioData);
    }
}