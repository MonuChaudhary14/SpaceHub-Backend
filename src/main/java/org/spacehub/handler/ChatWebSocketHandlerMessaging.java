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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandlerMessaging extends TextWebSocketHandler{

  private final MessageQueueService messageQueueService;
  private final IMessageService messageService;
  private final S3Service s3Service;
  private final ObjectMapper objectMapper;

  private final Map<String, WebSocketSession> activeUsers = new ConcurrentHashMap<>();
  private final Map<WebSocketSession, Map<String, String>> sessionMetadata = new ConcurrentHashMap<>();

  public ChatWebSocketHandlerMessaging(MessageQueueService messageQueueService,
                                       IMessageService messageService,
                                       S3Service s3Service) {
    this.messageQueueService = messageQueueService;
    this.messageService = messageService;
    this.s3Service = s3Service;
    this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
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

    List<Message> history = messageService.getChat(senderEmail, receiverEmail);
    if (history != null && !history.isEmpty()) {
      List<Map<String, Object>> formatted = new ArrayList<>();

      for (Message mess : history) {
        Map<String, Object> payload = buildPayload(mess);

        if ("FILE".equalsIgnoreCase(mess.getType()) && mess.getFileKey() != null) {
          String previewUrl = s3Service.generatePresignedDownloadUrl(mess.getFileKey(), Duration.ofMinutes(10));
          payload.put("previewUrl", previewUrl);
        }

        formatted.add(payload);
      }

      Map<String, Object> payload = Map.of(
              "type", "history",
              "chatWith", receiverEmail,
              "messages", formatted
      );
      session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }
  }

  @Override
  protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage textMessage) throws Exception {
    Map<String, String> meta = sessionMetadata.get(session);
    if (meta == null) {
      sendSystemMessage(session, "Session metadata missing. Reconnect required.");
      return;
    }

    String senderEmail = meta.get("senderEmail");
    String receiverEmail = meta.get("receiverEmail");

    Map<String, Object> clientPayload = objectMapper.readValue(textMessage.getPayload(), new TypeReference<>() {});
    String type = (String) clientPayload.getOrDefault("type", "MESSAGE");

    Message mess;
    if ("FILE".equalsIgnoreCase(type)) {
      String fileKey = (String) clientPayload.get("fileKey");
      String fileName = (String) clientPayload.get("fileName");
      String contentType = (String) clientPayload.get("contentType");

      String previewUrl = s3Service.generatePresignedDownloadUrl(fileKey, Duration.ofMinutes(10));

      mess = Message.builder()
              .senderEmail(senderEmail)
              .receiverEmail(receiverEmail)
              .content("[File] " + fileName)
              .fileName(fileName)
              .fileKey(fileKey)
              .contentType(contentType)
              .timestamp(LocalDateTime.now())
              .type("FILE")
              .build();

      messageQueueService.enqueue(mess);
      messageService.saveMessage(mess);

      Map<String, Object> payload = buildPayload(mess);
      payload.put("previewUrl", previewUrl);

      sendToUser(senderEmail, payload);
      sendToUser(receiverEmail, payload);

    }
    else {
      mess = Message.builder()
              .senderEmail(senderEmail)
              .receiverEmail(receiverEmail)
              .content((String) clientPayload.get("content"))
              .timestamp(LocalDateTime.now())
              .type("MESSAGE")
              .build();

      messageQueueService.enqueue(mess);
      messageService.saveMessage(mess);

      sendToReceiver(receiverEmail, buildPayload(mess));
      sendToReceiver(senderEmail, buildPayload(mess));
    }
  }

  private void sendToReceiver(String email, Map<String, Object> payload) throws IOException {
    WebSocketSession session = activeUsers.get(email);
    if (session != null && session.isOpen()) {
      session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }
  }

  private Map<String, Object> buildPayload(Message message) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", message.getType());
    payload.put("senderEmail", message.getSenderEmail());
    payload.put("receiverEmail", message.getReceiverEmail());
    payload.put("content", message.getContent());
    payload.put("timestamp", message.getTimestamp().toString());

    if ("FILE".equalsIgnoreCase(message.getType())) {
      payload.put("fileName", message.getFileName());
      payload.put("fileKey", message.getFileKey());
      payload.put("contentType", message.getContentType());
    }

    return payload;
  }

//  private void sendToBoth(String senderEmail, String receiverEmail, Message message) throws IOException {
//    String json = objectMapper.writeValueAsString(message);
//
  ////    WebSocketSession senderSession = activeUsers.get(senderEmail);
  ////    if (senderSession != null && senderSession.isOpen()) {
  ////      senderSession.sendMessage(new TextMessage(json));
  ////    }
//
//    WebSocketSession receiverSession = activeUsers.get(receiverEmail);
//    if (receiverSession != null && receiverSession.isOpen()) {
//      receiverSession.sendMessage(new TextMessage(json));
//    }
//  }

  @Override
  public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
    Map<String, String> meta = sessionMetadata.remove(session);
    if (meta != null) {
      activeUsers.remove(meta.get("senderEmail"));
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
    Map<String, Object> sys = Map.of(
            "type", "system",
            "system", content,
            "timestamp", LocalDateTime.now().toString()
    );
    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(sys)));
  }

}