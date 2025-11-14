package org.spacehub.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.entities.User.User;
import org.spacehub.repository.User.UserRepository;
import org.spacehub.service.Friend.FriendService;
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
  private final Map<String, String> usernameCache = new ConcurrentHashMap<>();
  private final FriendService friendService;

  public ChatWebSocketHandlerMessaging(
          MessageQueueService messageQueueService,
          IMessageService messageService,
          S3Service s3Service,
          UserRepository userRepository,
          FriendService friendService) {

    this.messageQueueService = messageQueueService;
    this.messageService = messageService;
    this.s3Service = s3Service;
    this.userRepository = userRepository;
    this.friendService = friendService;

    this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  @Override
  public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
    Map<String, String> params = parseQueryParams(session);
    String senderEmail = params.get("senderEmail");
    String receiverEmail = params.get("receiverEmail");

    if (senderEmail == null) {
      sendSystemMessage(session, "Missing senderEmail in connection URL");
      session.close(CloseStatus.BAD_DATA);
      return;
    }

    if (userRepository.findByEmail(senderEmail).isEmpty()) {
      sendSystemMessage(session, "Sender does not exist.");
      session.close(CloseStatus.BAD_DATA);
      return;
    }

    activeUsers.put(senderEmail, session);
    sessionMetadata.put(session, params);
    sendSystemMessage(session, "Connected as " + senderEmail);

    processUnreadMessages(session, senderEmail);

    if (receiverEmail != null) {

      if (userRepository.findByEmail(receiverEmail).isEmpty()) {
        sendSystemMessage(session, "Receiver not found.");
        session.close(CloseStatus.BAD_DATA);
        return;
      }

      if (friendService.areFriends(senderEmail, receiverEmail)) {
        sendSystemMessage(session, "You can only chat with friends.");
        session.close(CloseStatus.NOT_ACCEPTABLE);
        return;
      }
    }

    processUnreadMessages(session, senderEmail);

    if (receiverEmail != null) {
      processHistoryForReceiver(session, senderEmail, receiverEmail);
    }
  }

  private void processUnreadMessages(WebSocketSession session, String senderEmail) throws Exception {
    List<Message> unread = messageService.getUnreadMessages(senderEmail);
    if (unread == null || unread.isEmpty()) return;

    List<Map<String, Object>> formatted = new ArrayList<>();
    for (Message message : unread) {
      formatted.add(buildPayload(message));
    }

    Map<String, Object> unreadPayload = Map.of(
            "type", "unread",
            "count", formatted.size(),
            "messages", formatted
    );
    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(unreadPayload)));
  }

  private void processHistoryForReceiver(WebSocketSession session, String senderEmail, String receiverEmail)
          throws Exception {
    List<Message> history = messageService.getChat(senderEmail, receiverEmail);
    if (history == null || history.isEmpty()) return;

    List<Map<String, Object>> formatted = new ArrayList<>();
    for (Message message : history) {
      Map<String, Object> payload = buildPayload(message);
      addPreviewIfFile(payload, message.getType(), message.getFileKey());
      formatted.add(payload);
    }

    Map<String, Object> payload = Map.of("type", "history", "chatWith", receiverEmail, "messages", formatted);
    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
  }

  private void sendChatSummary(WebSocketSession session, String senderEmail) throws Exception {
    List<String> partners = messageService.getAllChatPartners(senderEmail);
    if (partners == null || partners.isEmpty()) {
      Map<String, Object> empty = Map.of("type", "chatSummary", "rooms", List.of());
      session.sendMessage(new TextMessage(objectMapper.writeValueAsString(empty)));
      return;
    }

    List<Map<String, Object>> summary = partners.stream()
            .map(partner -> Map.<String, Object>of(
                    "chatPartner", partner,
                    "unreadCount", messageService.countUnreadMessagesInChat(senderEmail, partner)
            )).toList();

    Map<String, Object> summaryPayload = Map.of("type", "chatSummary", "rooms", summary);
    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(summaryPayload)));
  }

  private void addPreviewIfFile(Map<String, Object> payload, String type, String fileKey) {
    if ("FILE".equalsIgnoreCase(type) && fileKey != null) {
      try {
        String previewUrl = s3Service.generatePresignedDownloadUrl(fileKey, Duration.ofMinutes(10));
        payload.put("previewUrl", previewUrl);
      } catch (Exception ignored) {}
    }
  }

  @Override
  protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage textMessage)
          throws Exception {

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
      case "READ" -> handleReadMessages(clientPayload);
      case "SUMMARY" -> sendChatSummary(session, senderEmail);
      default -> handleTextOnlyMessage(senderEmail, receiverEmail, clientPayload);
    }
  }

  private void handleTextOnlyMessage(String senderEmail, String receiverEmail, Map<String, Object> payload) throws IOException {

    if (friendService.areFriends(senderEmail, receiverEmail)) {
      throw new RuntimeException("Cannot message non-friends.");
    }

    Message mess = Message.builder()
            .messageUuid(UUID.randomUUID().toString())
            .senderEmail(senderEmail)
            .receiverEmail(receiverEmail)
            .content((String) payload.get("content"))
            .timestamp(LocalDateTime.now())
            .type("MESSAGE")
            .build();

    messageQueueService.enqueue(mess);
    Map<String, Object> sendPayload = buildPayload(mess);
    sendPayload.put("optimistic", true);
    sendToReceiver(senderEmail, sendPayload);
    sendToReceiver(receiverEmail, sendPayload);
  }

  private void handleFileMessage(String senderEmail, String receiverEmail, Map<String, Object> payload) throws IOException {

    if (friendService.areFriends(senderEmail, receiverEmail)) {
      throw new RuntimeException("Cannot message non-friends.");
    }

    String fileKey = (String) payload.get("fileKey");
    String fileName = (String) payload.get("fileName");
    String contentType = (String) payload.get("contentType");

    String previewUrl = s3Service.generatePresignedDownloadUrl(fileKey, Duration.ofMinutes(15));

    Message mess = Message.builder()
            .messageUuid(UUID.randomUUID().toString())
            .senderEmail(senderEmail)
            .receiverEmail(receiverEmail)
            .content(fileName)
            .fileName(fileName)
            .fileKey(fileKey)
            .contentType(contentType)
            .timestamp(LocalDateTime.now())
            .type("FILE")
            .build();

    messageQueueService.enqueue(mess);
    Map<String, Object> payloadSend = buildPayload(mess);
    payloadSend.put("previewUrl", previewUrl);
    payloadSend.put("optimistic", true);
    sendToReceiver(senderEmail, payloadSend);
    sendToReceiver(receiverEmail, payloadSend);
  }

  private void handleReadMessages(Map<String, Object> payload) {
    Object ids = payload.get("messageUuids");
    if (ids instanceof List<?> list) {
      for (Object id : list) {
        try {
          messageService.markAsReadByUuid(id.toString());
        } catch (Exception ignored) {}
      }
    }
  }

  private void handleDeleteMessage(String senderEmail, String receiverEmail, Map<String, Object> payload)
          throws IOException {

    Object uuidObj = payload.get("messageUuid");
    if (uuidObj == null) {
      sendSystemMessage(activeUsers.get(senderEmail), "Missing messageUuid for DELETE action");
      return;
    }

    String messageUuid = uuidObj.toString();
    boolean deleted = messageQueueService.deleteMessageByUuid(messageUuid);

    if (!deleted) {
      sendSystemMessage(activeUsers.get(senderEmail), "Message not found or already deleted");
      return;
    }

    Map<String, Object> resp = Map.of(
            "type", "DELETE",
            "messageUuid", messageUuid,
            "deletedBy", senderEmail,
            "timestamp", LocalDateTime.now().toString()
    );
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
    payload.put("messageId", message.getId());
    payload.put("messageUuid", message.getMessageUuid());
    payload.put("type", message.getType());
    payload.put("senderEmail", message.getSenderEmail());
    payload.put("receiverEmail", message.getReceiverEmail());
    payload.put("content", message.getContent());
    payload.put("timestamp", message.getTimestamp().toString());
    payload.put("readStatus", message.getReadStatus());
    payload.put("senderDeleted", message.getSenderDeleted());
    payload.put("receiverDeleted", message.getReceiverDeleted());
    payload.put("senderUsername", getUsername(message.getSenderEmail()));
    payload.put("receiverUsername", getUsername(message.getReceiverEmail()));

    if ("FILE".equalsIgnoreCase(message.getType())) {
      payload.put("fileName", message.getFileName());
      payload.put("fileKey", message.getFileKey());
      payload.put("contentType", message.getContentType());
    }
    return payload;
  }

  private String getUsername(String email) {
    return usernameCache.computeIfAbsent(email, e ->
            userRepository.findByEmail(e).map(User::getUsername).orElse(null));
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
  public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) throws Exception {
    Map<String, String> meta = sessionMetadata.remove(session);
    if (meta != null) {
      activeUsers.remove(meta.get("senderEmail"));
    }
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

  public void broadcastMessageToUsers(Message message) {
    try {
      Map<String, Object> payload = buildPayload(message);
      payload.put("optimistic", false);
      sendToReceiver(message.getSenderEmail(), payload);
      sendToReceiver(message.getReceiverEmail(), payload);
    } catch (Exception ignored) {}
  }

}
