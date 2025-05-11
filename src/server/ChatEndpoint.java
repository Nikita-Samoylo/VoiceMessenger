package server;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@ServerEndpoint("/chat")
public class ChatEndpoint {
    private static final Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        System.out.println("New connection: " + session.getId());
    }

    @OnMessage
    public void onMessage(String message, Session session) throws IOException {
        broadcast(message, session);
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        System.out.println("Closed connection: " + session.getId());
    }

    @OnError
    public void onError(Session session, Throwable thr) {
        System.err.println("Error for " + session.getId() + ": " + thr.getMessage());
    }

    private void broadcast(String message, Session sender) throws IOException {
        for (Session s : sessions) {
            if (s.isOpen() && !s.equals(sender)) {
                s.getBasicRemote().sendText(message);
            }
        }
    }
}