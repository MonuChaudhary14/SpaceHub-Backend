package org.spacehub.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.service.MessageQueueService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandlerMessaging extends TextWebSocketHandler{

    private final MessageQueueService messageQueueService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, WebSocketSession> activeUsers = new ConcurrentHashMap<>();

    public ChatWebSocketHandlerMessaging(MessageQueueService messageQueueService) {
        this.messageQueueService = messageQueueService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("WebSocket connection from " + session.getRemoteAddress());
        sendSystemMessage(session, "Connected to Direct Messaging WebSocket");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            Map<String, Object> data = objectMapper.readValue(message.getPayload(), Map.class);

            String senderEmail = (String) data.get("senderEmail");
            String receiverEmail = (String) data.get("receiverEmail");
            String content = (String) data.get("content");

            if (senderEmail == null || receiverEmail == null || content == null) {
                sendSystemMessage(session, "Missing senderEmail, receiverEmail, or content");
                return;
            }

            activeUsers.putIfAbsent(senderEmail, session);

            Message chatMessage = Message.builder()
                    .senderEmail(senderEmail)
                    .receiverEmail(receiverEmail)
                    .content(content)
                    .timestamp(LocalDateTime.now())
                    .build();

            messageQueueService.enqueue(chatMessage);

            WebSocketSession receiverSession = activeUsers.get(receiverEmail);
            if (receiverSession != null && receiverSession.isOpen()) {
                receiverSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(chatMessage)));
            }

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(chatMessage)));

        }
        catch (Exception e) {
            e.printStackTrace();
            sendSystemMessage(session,"Error: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        activeUsers.entrySet().removeIf(entry -> entry.getValue().equals(session));
        System.out.println("Connection closed: " + status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("Transport error: " + exception.getMessage());
        session.close(CloseStatus.SERVER_ERROR);
    }

    private void sendSystemMessage(WebSocketSession session, String content) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("system", content))));
    }

    public Set<String> getActiveUserEmails() {
        return activeUsers.keySet();
    }

}
