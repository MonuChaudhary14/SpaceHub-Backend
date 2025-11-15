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
import org.spacehub.service.File.S3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandlerMessaging extends TextWebSocketHandler {

  private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandlerMessaging.class);

  private final MessageQueueService messageQueueService;
  private final IMessageService messageService;
  private final S3Service s3Service;
  private final UserRepository userRepository;
  private final ObjectMapper objectMapper;
  private final FriendService friendService;

  private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
  private final Map<WebSocketSession, String> sessionRoom = new ConcurrentHashMap<>();
  private final Map<WebSocketSession, String> userSessions = new ConcurrentHashMap<>();
  private final Map<String, Set<WebSocketSession>> activeUsers = new ConcurrentHashMap<>();
  private final Map<WebSocketSession, Map<String, String>> sessionMetadata = new ConcurrentHashMap<>();
  private final Map<String, String> usernameCache = new ConcurrentHashMap<>();

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
  public void afterConnectionEstablished(@NonNull WebSocketSession session) {
    Map<String, String> params = parseQueryParams(session);
    String senderEmailRaw = params.get("senderEmail");
    String receiverEmailRaw = params.get("receiverEmail");

    if (isBlank(senderEmailRaw)) {
      sendAndClose(session, "Missing senderEmail in connection URL", CloseStatus.BAD_DATA);
      return;
    }

    String senderEmail = normalizeEmail(senderEmailRaw);
    String receiverEmail = normalizeOrNull(receiverEmailRaw);

    if (!validateSender(session, senderEmail)) {
      return;
    }

    registerSession(session, params, senderEmail, receiverEmail);

    sendSafe(session, "Connected as " + senderEmailRaw);

    runSafely(() -> {
      try {
        processUnreadMessages(session, senderEmail);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }, "Unable to load unread messages");

    if (!isBlank(receiverEmail)) {
      handleReceiverFlow(session, senderEmail, receiverEmail);
    }

    logger.info("WS connected: {} (rooms: {})", senderEmail, activeUsers.keySet());
  }

  private String normalizeOrNull(String email) {
    return Optional.ofNullable(email)
      .filter(s -> !s.isBlank())
      .map(this::normalizeEmail)
      .orElse(null);
  }

  private boolean validateSender(WebSocketSession session, String senderEmail) {
    try {
      if (userRepository.findByEmail(senderEmail).isEmpty()) {
        sendAndClose(session, "Sender does not exist.", CloseStatus.BAD_DATA);
        return false;
      }
      return true;
    } catch (Exception e) {
      logger.error("Error validating sender", e);
      sendAndClose(session, "Error validating sender — try again later.", CloseStatus.SERVER_ERROR);
      return false;
    }
  }

  private void registerSession(WebSocketSession session,
                               Map<String, String> params,
                               String senderEmail,
                               String receiverEmail) {
    sessionMetadata.put(session, params);
    activeUsers.computeIfAbsent(senderEmail, k -> ConcurrentHashMap.newKeySet()).add(session);
    userSessions.put(session, senderEmail);

    if (!isBlank(receiverEmail)) {
      String chatKey = messageQueueService.buildChatKey(senderEmail, receiverEmail);
      rooms.computeIfAbsent(chatKey, k -> ConcurrentHashMap.newKeySet()).add(session);
      sessionRoom.put(session, chatKey);
    }
  }

  private void handleReceiverFlow(WebSocketSession session, String senderEmail, String receiverEmail) {
    try {
      if (userRepository.findByEmail(receiverEmail).isEmpty()) {
        sendSafe(session, "Receiver not found.");
        return;
      }

      if (friendService.areFriends(senderEmail, receiverEmail)) {
        sendSafe(session, "You can only chat with friends.");
        return;
      }

      runSafely(() -> {
        try {
          processHistoryForReceiver(session, senderEmail, receiverEmail);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      },
        "Unable to load chat history");

    } catch (Exception e) {
      logger.error("Error validating receiver", e);
      sendSafe(session, "Unable to validate receiver at the moment.");
    }
  }

  private void sendSafe(WebSocketSession session, String message) {
    try {
      sendSystemMessage(session, message);
    } catch (IOException ignored) {}
  }

  private void sendAndClose(WebSocketSession session, String message, CloseStatus closeStatus) {
    try {
      sendSystemMessage(session, message);
    } catch (IOException ignored) {}
    closeSafe(session, closeStatus);
  }

  private void closeSafe(WebSocketSession session, CloseStatus status) {
    try {
      session.close(status);
    } catch (IOException ignored) {}
  }

  private void runSafely(Runnable action, String logPrefix) {
    try {
      action.run();
    } catch (Exception e) {
      logger.warn(logPrefix, e);
    }
  }

  private boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private void processUnreadMessages(WebSocketSession session, String senderEmail) throws Exception {
    List<Message> unread = messageService.getUnreadMessages(senderEmail);
    if (unread == null || unread.isEmpty()) {
      return;
    }
    List<Map<String, Object>> formatted = new ArrayList<>();
    for (Message message : unread) {
      try {
        if (shouldHideForRequester(message, senderEmail)) {
          continue;
        }
        Map<String, Object> payload = buildPayload(message);
        boolean pending = messageQueueService.isPending(message.getMessageUuid());
        payload.put("optimistic", pending);
        addPreviewIfFileQuiet(payload, message.getType(), message.getFileKey());
        formatted.add(payload);
      } catch (Exception ignored) {}
    }
    formatted.sort(Comparator.comparingLong(m -> ((Number) m.get("timestamp")).longValue()));
    Map<String, Object> unreadPayload = Map.of("type", "unread", "count", formatted.size(),
      "messages", formatted);
    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(unreadPayload)));
  }

  private void processHistoryForReceiver(WebSocketSession session, String senderEmail, String receiverEmail)
    throws Exception {
    List<Message> dbMessages = messageService.getChat(senderEmail, receiverEmail);
    if (dbMessages == null) {
      dbMessages = Collections.emptyList();
    }
    List<Message> pending = messageQueueService.getPendingForChat(senderEmail, receiverEmail);
    if (pending == null) {
      pending = Collections.emptyList();
    }
    List<Message> filteredDb = new ArrayList<>();
    for (Message m : dbMessages) {
      if (!shouldHideForRequester(m, senderEmail) && m.getDeletedAt() == null) {
        filteredDb.add(m);
      }
    }
    List<Message> filteredPending = new ArrayList<>();
    for (Message m : pending) {
      if (!shouldHideForRequester(m, senderEmail)) {
        filteredPending.add(m);
      }
    }
    List<Map<String, Object>> formatted = mergeAndFormatMessages(filteredDb, filteredPending);
    Map<String, Object> payload = Map.of("type", "history", "chatWith", receiverEmail, "messages",
      formatted);
    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
  }

  private List<Map<String, Object>> mergeAndFormatMessages(List<Message> dbMessages, List<Message> pendingMessages) {
    Map<String, MessageEntry> byUuid = new LinkedHashMap<>();
    for (Message m : dbMessages) {
      if (m != null && m.getMessageUuid() != null) {
        byUuid.put(m.getMessageUuid(), new MessageEntry(m, false));
      }
    }
    for (Message m : pendingMessages) {
      if (m != null && m.getMessageUuid() != null) {
        byUuid.put(m.getMessageUuid(), new MessageEntry(m, true));
      }
    }
    List<MessageEntry> entries = new ArrayList<>(byUuid.values());
    entries.sort((a, b) -> {
      long ta = Objects.requireNonNullElse(a.msg.getTimestamp(), 0L);
      long tb = Objects.requireNonNullElse(b.msg.getTimestamp(), 0L);
      int cmp = Long.compare(ta, tb);
      if (cmp != 0) {
        return cmp;
      }
      return a.msg.getMessageUuid().compareTo(b.msg.getMessageUuid());
    });
    List<Map<String, Object>> formatted = new ArrayList<>();
    for (MessageEntry e : entries) {
      try {
        Map<String, Object> payload = buildPayload(e.msg);
        payload.put("optimistic", e.optimistic);
        addPreviewIfFileQuiet(payload, e.msg.getType(), e.msg.getFileKey());
        formatted.add(payload);
      } catch (Exception ignored) {}
    }
    return formatted;
  }

  private record MessageEntry(Message msg, boolean optimistic) {}

  private boolean shouldHideForRequester(Message m, String requesterEmail) {
    if (m == null || requesterEmail == null) {
      return false;
    }
    if (requesterEmail.equalsIgnoreCase(m.getSenderEmail()) && Boolean.TRUE.equals(m.getSenderDeleted())) {
      return true;
    }
    if (requesterEmail.equalsIgnoreCase(m.getReceiverEmail()) && Boolean.TRUE.equals(m.getReceiverDeleted())) {
      return true;
    }
    return m.getDeletedAt() != null;
  }

  private void addPreviewIfFileQuiet(Map<String, Object> payload, String type, String fileKey) {
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
    String senderEmail = userSessions.get(session);
    String chatKey = sessionRoom.get(session);
    Map<String, Object> clientPayload = objectMapper.readValue(textMessage.getPayload(), new TypeReference<>() {});
    String type = (String) clientPayload.getOrDefault("type", "MESSAGE");
    switch (type.toUpperCase()) {
      case "FILE" -> handleFileMessage(chatKey, senderEmail, clientPayload, session);
      case "DELETE" -> handleDeleteMessage(chatKey, senderEmail, clientPayload, session);
      case "READ" -> handleReadMessages(clientPayload);
      case "SUMMARY" -> sendChatSummary(session, senderEmail);
      case "HISTORY" -> {
        Object cp = clientPayload.get("chatWith");
        if (cp instanceof String chatWith) {
          processHistoryForReceiver(session, senderEmail, chatWith.toLowerCase(Locale.ROOT));
        } else {
          sendSystemMessage(session, "Missing chatWith for HISTORY request.");
        }
      }
      default -> handleTextOnlyMessage(chatKey, senderEmail, clientPayload, session);
    }
  }

  private void handleTextOnlyMessage(String chatKey, String senderEmail, Map<String, Object> payload,
                                     WebSocketSession senderSession) throws IOException {
    String receiverEmail = deriveOtherFromChatKey(chatKey, senderEmail);
    if (receiverEmail == null) {
      sendSystemMessage(senderSession, "No chat partner specified.");
      return;
    }
    if (friendService.areFriends(senderEmail, receiverEmail)) {
      sendSystemMessage(senderSession, "Cannot message non-friends.");
      return;
    }
    String messageUuid = UUID.randomUUID().toString();
    long ts = Instant.now().toEpochMilli();
    Message mess = Message.builder()
            .messageUuid(messageUuid)
            .senderEmail(senderEmail)
            .receiverEmail(receiverEmail)
            .content((String) payload.get("content"))
            .timestamp(ts)
            .type("MESSAGE")
            .build();
    messageQueueService.enqueue(mess);
    Map<String, Object> sendPayload = buildPayload(mess);
    sendPayload.put("optimistic", true);
    sendToUsers(Set.of(senderEmail), sendPayload);
  }

  private void handleFileMessage(String chatKey, String senderEmail, Map<String, Object> payload,
                                 WebSocketSession senderSession) throws IOException {
    String receiverEmail = deriveOtherFromChatKey(chatKey, senderEmail);
    if (receiverEmail == null) {
      sendSystemMessage(senderSession, "No chat partner specified.");
      return;
    }
    if (friendService.areFriends(senderEmail, receiverEmail)) {
      sendSystemMessage(senderSession, "Cannot message non-friends.");
      return;
    }
    String fileKey = (String) payload.get("fileKey");
    String fileName = (String) payload.get("fileName");
    String contentType = (String) payload.get("contentType");
    String previewUrl = null;
    if (fileKey != null) {
      try {
        previewUrl = s3Service.generatePresignedDownloadUrl(fileKey, Duration.ofMinutes(15));
      } catch (Exception ignored) {}
    }
    String messageUuid = UUID.randomUUID().toString();
    long ts = Instant.now().toEpochMilli();
    Message mess = Message.builder()
            .messageUuid(messageUuid)
            .senderEmail(senderEmail)
            .receiverEmail(receiverEmail)
            .content(fileName)
            .fileName(fileName)
            .fileKey(fileKey)
            .contentType(contentType)
            .timestamp(ts)
            .type("FILE")
            .build();
    messageQueueService.enqueue(mess);
    Map<String, Object> payloadSend = buildPayload(mess);
    if (previewUrl != null) {
      payloadSend.put("previewUrl", previewUrl);
    }
    payloadSend.put("optimistic", true);
    sendToUsers(Set.of(senderEmail), payloadSend);
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

  private void handleDeleteMessage(String chatKey, String senderEmail, Map<String, Object> payload,
                                   WebSocketSession senderSession) throws IOException {
    Object uuidObj = payload.get("messageUuid");
    if (uuidObj == null) {
      sendSystemMessage(senderSession, "Missing messageUuid for DELETE action");
      return;
    }
    String messageUuid = uuidObj.toString();
    boolean removed = messageQueueService.deleteMessageByUuid(messageUuid);
    if (!removed) {
      sendSystemMessage(senderSession, "Message not found or already deleted");
      return;
    }
    String receiverEmail = deriveOtherFromChatKey(chatKey, senderEmail);
    Map<String, Object> resp = Map.of("type", "DELETE", "messageUuid", messageUuid, "deletedBy",
      senderEmail, "timestamp", Instant.now().toEpochMilli());
    if (receiverEmail != null) {
      sendToUsers(Set.of(senderEmail, receiverEmail), resp);
    } else {
      sendToUsers(Set.of(senderEmail), resp);
    }
  }

  private void sendChatSummary(WebSocketSession session, String senderEmail) throws Exception {
    List<String> partners = messageService.getAllChatPartners(senderEmail);
    if (partners == null || partners.isEmpty()) {
      Map<String, Object> empty = Map.of("type", "chatSummary", "rooms", List.of());
      session.sendMessage(new TextMessage(objectMapper.writeValueAsString(empty)));
      return;
    }
    List<Map<String, Object>> summary = new ArrayList<>();
    for (String partner : partners) {
      summary.add(Map.of("chatPartner", partner, "unreadCount",
        messageService.countUnreadMessagesInChat(senderEmail, partner)));
    }
    Map<String, Object> summaryPayload = Map.of("type", "chatSummary", "rooms", summary);
    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(summaryPayload)));
  }

  private void sendToUsers(Set<String> emails, Map<String, Object> payload) throws IOException {
    if (emails == null || emails.isEmpty()) {
      return;
    }
    String json = objectMapper.writeValueAsString(payload);
    Set<WebSocketSession> targets = new LinkedHashSet<>();
    for (String email : emails) {
      Set<WebSocketSession> sessions = activeUsers.get(email.toLowerCase(Locale.ROOT));
      if (sessions != null) {
        targets.addAll(sessions);
      }
    }
    for (WebSocketSession session : targets) {
      if (session != null && session.isOpen()) {
        session.sendMessage(new TextMessage(json));
      }
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
    payload.put("message", message.getContent());
    long epochMillis = Objects.requireNonNullElse(message.getTimestamp(), Instant.now().toEpochMilli());
    payload.put("timestamp", epochMillis);
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
    if (email == null) {
      return null;
    }
    return usernameCache.computeIfAbsent(email,
      e -> userRepository.findByEmail(e).map(User::getUsername).orElse(null));
  }

  private String deriveOtherFromChatKey(String chatKey, String selfEmail) {
    if (chatKey == null || selfEmail == null) {
      return null;
    }
    String[] parts = chatKey.split("::", 2);
    if (parts.length != 2) {
      return null;
    }
    String a = parts[0];
    String b = parts[1];
    String lowerSelf = selfEmail.toLowerCase(Locale.ROOT);
    if (lowerSelf.equals(a)) {
      return b;
    }
    if (lowerSelf.equals(b)) {
      return a;
    }
    return null;
  }

  @Override
  public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
    cleanupSession(session);
    logger.info("WS closed: {} status: {}", userSessions.get(session), status);
  }

  @Override
  public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
    cleanupSession(session);

    if (session.isOpen()) {
      try {
        session.close(CloseStatus.SERVER_ERROR);
      } catch (IOException ignored) {}
    }

    logger.error("Transport error for session", exception);
  }

  private void cleanupSession(@NonNull WebSocketSession session) {
    sessionMetadata.remove(session);

    String email = userSessions.remove(session);
    String room = sessionRoom.remove(session);

    if (email != null) {
      final String emailKey = email.toLowerCase(Locale.ROOT);
      activeUsers.computeIfPresent(emailKey, (k, sessions) -> {
        sessions.remove(session);
        return sessions.isEmpty() ? null : sessions;
      });
    }

    if (room != null) {
      rooms.computeIfPresent(room, (k, sessions) -> {
        sessions.remove(session);
        if (sessions.isEmpty()) {
          return null;
        }
        return sessions;
      });
    }
  }

  private Map<String, String> parseQueryParams(WebSocketSession session) {
    Map<String, String> map = new HashMap<>();
    if (session.getUri() == null) return map;
    String query = session.getUri().getQuery();
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
    if (session == null || !session.isOpen()) {
      return;
    }
    Map<String, Object> sys = Map.of("type", "system", "system", content, "timestamp",
      Instant.now().toEpochMilli());
    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(sys)));
  }

  public void confirmAndBroadcast(Message message) {
    try {
      Map<String, Object> payload = buildPayload(message);
      payload.put("optimistic", false);
      addPreviewIfFileQuiet(payload, message.getType(), message.getFileKey());
      Map<String, Object> confirm = Map.of("type", "CONFIRM", "messageUuid", message.getMessageUuid(),
        "messageId", message.getId(), "timestamp", message.getTimestamp(), "message", payload);
      try {
        sendToUsers(Set.of(message.getSenderEmail(), message.getReceiverEmail()), confirm);
      } catch (IOException ignored) {}
      try {
        sendToUsers(Set.of(message.getSenderEmail(), message.getReceiverEmail()), payload);
      } catch (IOException ignored) {}
    } catch (Exception e) {
      logger.error("Error in confirmAndBroadcast", e);
    }
  }

  private String normalizeEmail(String email) {
    if (email == null) {
      return null;
    }
    return email.trim().toLowerCase(Locale.ROOT);
  }

}
