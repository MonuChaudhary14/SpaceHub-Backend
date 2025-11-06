package org.spacehub.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.service.Interface.IMessageService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandlerMessaging extends TextWebSocketHandler{

    private final IMessageService messageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, WebSocketSession> activeUsers = new ConcurrentHashMap<>();

    public ChatWebSocketHandlerMessaging(IMessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String email = extractEmailFromQuery(session);
        if (email != null) {
            activeUsers.put(email, session);
            System.out.println("User connected: " + email);
            sendSystemMessage(session, "Connected as " + email);
        }
        else {
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Message chatMessage = objectMapper.readValue(message.getPayload(), Message.class);

        messageService.saveMessage(chatMessage);

        WebSocketSession receiverSession = activeUsers.get(chatMessage.getReceiverId());
        if (receiverSession != null && receiverSession.isOpen()) {
            receiverSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(chatMessage)));
        }

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(chatMessage)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String email = extractEmailFromQuery(session);
        if (email != null) {
            activeUsers.remove(email);
            System.out.println("User disconnected: " + email);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("Transport error: " + exception.getMessage());
        session.close(CloseStatus.SERVER_ERROR);
    }

    private String extractEmailFromQuery(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query != null && query.contains("email=")) {
            return URLDecoder.decode(query.split("email=")[1], StandardCharsets.UTF_8);
        }
        return null;
    }

    private void sendSystemMessage(WebSocketSession session, String content) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("system", content))));
    }

}
