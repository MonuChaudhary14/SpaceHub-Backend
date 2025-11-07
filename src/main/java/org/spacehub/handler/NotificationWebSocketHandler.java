package org.spacehub.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler{

    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String email = getEmailFromSession(session);
        if (email != null) {
            userSessions.put(email, session);
            System.out.println("Connected: " + email);
        }
        else {
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        System.out.println("Message from client: " + message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String email = getEmailFromSession(session);
        if (email != null) {
            userSessions.remove(email);
            System.out.println("Disconnected: " + email);
        }
    }

    private String getEmailFromSession(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query == null) return null;
        for (String param : query.split("&")) {
            if (param.startsWith("email=")) {
                return param.split("=")[1];
            }
        }
        return null;
    }

    public void sendNotification(String email, Object notificationData) {
        WebSocketSession session = userSessions.get(email);
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(notificationData);
                session.sendMessage(new TextMessage(json));
                System.out.println("Sent notification to " + email);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            System.out.println("No active WebSocket session for: " + email);
        }
    }

}
