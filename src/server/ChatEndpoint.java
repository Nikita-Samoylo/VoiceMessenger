package server;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;
import shared.TextMessage;
import shared.VoiceMessage;

@ServerEndpoint("/chat")
public class ChatEndpoint {
    private static final Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        System.out.println("New connection: " + session.getId());
    }

    @OnMessage
    public void onTextMessage(String message, Session session) throws IOException {
        try {
            JSONObject json = new JSONObject(message);
            if ("text".equals(json.optString("type"))) {
                String content = json.optString("content", "");
                if (content.length() > TextMessage.MAX_TEXT_LENGTH) {
                    session.getBasicRemote().sendText(
                            "{\"error\":\"Message too long. Max length is " +
                                    TextMessage.MAX_TEXT_LENGTH + " characters\"}"
                    );
                    return;
                }
            }
            broadcastText(message, session);
        } catch (JSONException e) {
            session.getBasicRemote().sendText("{\"error\":\"Invalid message format\"}");
        }
    }

    @OnMessage
    public void onBinaryMessage(byte[] data, Session session) throws IOException {
        if (data.length > VoiceMessage.MAX_AUDIO_SIZE_BYTES) {
            session.getBasicRemote().sendText(
                    "{\"error\":\"Audio message too large. Max size is " +
                            VoiceMessage.MAX_AUDIO_SIZE_BYTES/1024 + " KB\"}"
            );
            return;
        }
        broadcastBinary(data, session);
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

    private void broadcastText(String message, Session sender) throws IOException {
        for (Session s : sessions) {
            if (s.isOpen() && !s.equals(sender)) {
                s.getBasicRemote().sendText(message);
            }
        }
    }

    private void broadcastBinary(byte[] data, Session sender) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        for (Session s : sessions) {
            if (s.isOpen() && !s.equals(sender)) {
                s.getBasicRemote().sendBinary(buffer);
                buffer.rewind();
            }
        }
    }
}