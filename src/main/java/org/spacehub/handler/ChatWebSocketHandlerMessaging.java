package org.spacehub.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.service.MessageQueueService;
import org.spacehub.service.Interface.IMessageService;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandlerMessaging extends TextWebSocketHandler{

  private final MessageQueueService messageQueueService;
  private final IMessageService messageService;
  private final ObjectMapper objectMapper;

  private final Map<String, WebSocketSession> activeUsers = new ConcurrentHashMap<>();
  private final Map<WebSocketSession, Map<String, String>> sessionMetadata = new ConcurrentHashMap<>();

  public ChatWebSocketHandlerMessaging(MessageQueueService messageQueueService,
                                       IMessageService messageService) {
    this.messageQueueService = messageQueueService;
    this.messageService = messageService;
    this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  @Override
  public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
    Map<String, String> params = parseQueryParams(session);
    String senderEmail = params.get("senderEmail");
    String receiverEmail = params.get("receiverEmail");

    if (senderEmail == null || receiverEmail == null) {
      sendSystemMessage(session, "Missing senderEmail or receiverEmail in connection URL");
      session.close(CloseStatus.BAD_DATA);
      return;
    }

    activeUsers.put(senderEmail, session);
    sessionMetadata.put(session, params);

    sendSystemMessage(session, "Connected as " + senderEmail + " (chat with " + receiverEmail + ")");
    System.out.println("WebSocket connected: " + senderEmail + " <-> " + receiverEmail + " from " + session.getRemoteAddress());

    try {
      List<Message> history = messageService.getChat(senderEmail, receiverEmail);
      if (history != null && !history.isEmpty()) {
        Map<String, Object> payload = Map.of(
                "type", "history",
                "chatWith", receiverEmail,
                "messages", history
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
      }
    }
    catch (Exception ignored) {
    }
  }

  @Override
  protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage textMessage) throws Exception {
    try {
      Map<String, String> meta = sessionMetadata.get(session);
      if (meta == null) {
        sendSystemMessage(session, "Session metadata missing. Reconnect required.");
        return;
      }

      String senderEmail = meta.get("senderEmail");
      String receiverEmail = meta.get("receiverEmail");

      Map<String, Object> clientPayload = objectMapper.readValue(
        textMessage.getPayload(),
        new TypeReference<>() {
        }
      );

      String type = (String) clientPayload.getOrDefault("type", "MESSAGE");

      if ("MESSAGE".equalsIgnoreCase(type)) {
        String content = (String) clientPayload.get("content");

        if (content == null || content.trim().isEmpty()) {
          sendSystemMessage(session, "Message content cannot be empty");
          return;
        }

        Message chatMessage = Message.builder()
          .senderEmail(senderEmail)
          .receiverEmail(receiverEmail)
          .content(content)
          .timestamp(LocalDateTime.now())
          .build();

        messageQueueService.enqueue(chatMessage);
        sendToBoth(senderEmail, receiverEmail, chatMessage);
        return;
      }

      if ("FILE".equalsIgnoreCase(type)) {
        String fileName = (String) clientPayload.get("fileName");
        String fileUrl = (String) clientPayload.get("fileUrl");
        String contentType = (String) clientPayload.get("contentType");

        if (fileUrl == null || fileUrl.isBlank()) {
          sendSystemMessage(session, "File URL is missing");
          return;
        }

        Message fileMessage = Message.builder()
          .senderEmail(senderEmail)
          .receiverEmail(receiverEmail)
          .content("[File] " + fileName)
          .timestamp(LocalDateTime.now())
          .fileName(fileName)
          .fileUrl(fileUrl)
          .contentType(contentType)
          .build();

        messageQueueService.enqueue(fileMessage);
        sendToBoth(senderEmail, receiverEmail, fileMessage);
        return;
      }

      sendSystemMessage(session, "Unknown message type: " + type);

    } catch (IOException e) {
      sendSystemMessage(session, "Failed to process message: " + e.getMessage());
    }
  }

  private void sendToBoth(String senderEmail, String receiverEmail, Message message) throws IOException {
    String json = objectMapper.writeValueAsString(message);

    WebSocketSession senderSession = activeUsers.get(senderEmail);
    if (senderSession != null && senderSession.isOpen()) {
      senderSession.sendMessage(new TextMessage(json));
    }

    WebSocketSession receiverSession = activeUsers.get(receiverEmail);
    if (receiverSession != null && receiverSession.isOpen()) {
      receiverSession.sendMessage(new TextMessage(json));
    }
  }

  @Override
  public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
    Map<String, String> meta = sessionMetadata.remove(session);
    if (meta != null) {
      String senderEmail = meta.get("senderEmail");
      activeUsers.remove(senderEmail);
      System.out.println("WebSocket disconnected: " + senderEmail + " (" + status + ")");
    }
    else {
      System.out.println("WebSocket disconnected: unknown session (" + status + ")");
    }
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
    System.err.println("WebSocket transport error: " + exception.getMessage());
    if (session.isOpen()) {
      session.close(CloseStatus.SERVER_ERROR);
    }
  }

  private Map<String, String> parseQueryParams(WebSocketSession session) {
    Map<String, String> map = new HashMap<>();
    String query = Objects.requireNonNull(session.getUri()).getQuery();
    if (query != null && !query.isBlank()) {
      for (String param : query.split("&")) {
        String[] parts = param.split("=", 2);
        if (parts.length == 2) {
          map.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
      }
    }
    return map;
  }

  private void sendSystemMessage(WebSocketSession session, String content) throws IOException {
    Map<String, Object> sys = Map.of("type", "system", "system", content);
    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(sys)));
  }

}
