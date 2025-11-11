package org.spacehub.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.entities.User.User;
import org.spacehub.repository.User.UserRepository;
import org.spacehub.service.Message.MessageQueueService;
import org.spacehub.service.Interface.IMessageService;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.spacehub.service.File.S3Service;

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
  private final UserRepository userRepository;
  private final ObjectMapper objectMapper;

  private final Map<String, WebSocketSession> activeUsers = new ConcurrentHashMap<>();
  private final Map<WebSocketSession, Map<String, String>> sessionMetadata = new ConcurrentHashMap<>();

  public ChatWebSocketHandlerMessaging(MessageQueueService messageQueueService,
                                       IMessageService messageService,
                                       S3Service s3Service,
                                       UserRepository userRepository) {
    this.messageQueueService = messageQueueService;
    this.messageService = messageService;
    this.s3Service = s3Service;
    this.userRepository = userRepository;
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

        boolean isSender = senderEmail.equalsIgnoreCase(mess.getSenderEmail());
        if ((isSender && Boolean.TRUE.equals(mess.getSenderDeleted())) || (!isSender && Boolean.TRUE.equals(mess.getReceiverDeleted()))) {
          continue;
        }

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

    switch (type.toUpperCase()) {
      case "FILE" -> handleFileMessage(senderEmail, receiverEmail, clientPayload);
      case "DELETE" -> handleDeleteMessage(senderEmail, receiverEmail, clientPayload);
      default -> handleTextOnlyMessage(senderEmail, receiverEmail, clientPayload);
    }
  }

  private void handleTextOnlyMessage(String senderEmail, String receiverEmail, Map<String, Object> payload) throws IOException {
    Message mess = Message.builder()
            .senderEmail(senderEmail)
            .receiverEmail(receiverEmail)
            .content((String) payload.get("content"))
            .timestamp(LocalDateTime.now())
            .type("MESSAGE")
            .build();

    messageQueueService.enqueue(mess);
    Message saved = messageService.saveMessage(mess);

    Map<String, Object> sendPayload = buildPayload(saved);

    sendToReceiver(receiverEmail, sendPayload);
    sendToReceiver(senderEmail, sendPayload);
  }

  private void handleFileMessage(String senderEmail, String receiverEmail, Map<String, Object> payload) throws IOException {
    String fileKey = (String) payload.get("fileKey");
    String fileName = (String) payload.get("fileName");
    String contentType = (String) payload.get("contentType");

    String previewUrl = s3Service.generatePresignedDownloadUrl(fileKey, Duration.ofMinutes(10));

    Message mess = Message.builder()
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
    Message saved = messageService.saveMessage(mess);

    Map<String, Object> sendPayload = buildPayload(saved);
    sendPayload.put("previewUrl", previewUrl);

    sendToReceiver(senderEmail, sendPayload);
    sendToReceiver(receiverEmail, sendPayload);
  }

  private void handleDeleteMessage(String senderEmail, String receiverEmail, Map<String, Object> payload) throws IOException {
    Object objectID = payload.get("messageId");
    if (objectID == null) {
      sendSystemMessage(activeUsers.get(senderEmail), "Missing messageId for DELETE action");
      return;
    }

    Long messageId;
    try {
      if (objectID instanceof Number) {
        messageId = ((Number) objectID).longValue();
      }
      else {
        messageId = Long.parseLong(objectID.toString());
      }
    }
    catch (Exception e) {
      sendSystemMessage(activeUsers.get(senderEmail), "Invalid messageId format");
      return;
    }

    Message updated = messageService.deleteMessageForUser(messageId, senderEmail);
    if (updated == null) {
      sendSystemMessage(activeUsers.get(senderEmail), "Message not found or already deleted");
      return;
    }

    Map<String, Object> resp = new LinkedHashMap<>();
    resp.put("type", "DELETE");
    resp.put("messageId", String.valueOf(messageId));
    resp.put("deletedBy", senderEmail);
    resp.put("senderDeleted", updated.getSenderDeleted());
    resp.put("receiverDeleted", updated.getReceiverDeleted());
    resp.put("timestamp", LocalDateTime.now().toString());

    sendToReceiver(senderEmail, resp);
    sendToReceiver(receiverEmail, resp);
  }

  private void sendToReceiver(String email, Map<String, Object> payload) throws IOException {
    WebSocketSession session = activeUsers.get(email);
    if (session != null && session.isOpen()) {
      session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }
  }

  private Map<String, Object> buildPayload(Message message) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("messageId", message.getId() != null ? String.valueOf(message.getId()) : null);
    payload.put("type", message.getType());
    payload.put("senderEmail", message.getSenderEmail());
    payload.put("receiverEmail", message.getReceiverEmail());
    payload.put("content", message.getContent());
    payload.put("timestamp", message.getTimestamp() != null ? message.getTimestamp().toString() : null);
    payload.put("senderDeleted", message.getSenderDeleted());
    payload.put("receiverDeleted", message.getReceiverDeleted());

    try {
      Optional<User> sUser = userRepository.findByEmail(message.getSenderEmail());
      Optional<User> rUser = userRepository.findByEmail(message.getReceiverEmail());
      payload.put("senderUsername", sUser.map(User::getUsername).orElse(null));
      payload.put("receiverUsername", rUser.map(User::getUsername).orElse(null));
    }
    catch (Exception ignored) {
      payload.put("senderUsername", null);
      payload.put("receiverUsername", null);
    }

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