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
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class ChatWebSocketHandlerMessaging extends TextWebSocketHandler{

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
    String senderEmail = params.get("senderEmail");
    String receiverEmail = params.get("receiverEmail"); // optional

    if (senderEmail == null || senderEmail.isBlank()) {
      try {
        sendSystemMessage(session, "Missing senderEmail in connection URL");
        session.close(CloseStatus.BAD_DATA);
      }
      catch (IOException ignored) {}
      return;
    }

    try {
      if (userRepository.findByEmail(senderEmail).isEmpty()) {
        sendSystemMessage(session, "Sender does not exist.");
        try { session.close(CloseStatus.BAD_DATA); } catch (IOException ignored) {}
        return;
      }
    }
    catch (Exception e) {
      try {
        sendSystemMessage(session, "Error validating sender — try again later.");
        session.close(CloseStatus.SERVER_ERROR);
      }
      catch (IOException ignored) {}
      return;
    }

    sessionMetadata.put(session, params);

    activeUsers.computeIfAbsent(senderEmail.toLowerCase(), k -> ConcurrentHashMap.newKeySet()).add(session);
    userSessions.put(session, senderEmail.toLowerCase());

    if (receiverEmail != null && !receiverEmail.isBlank()) {
      String chatKey = messageQueueService.buildChatKey(senderEmail, receiverEmail);
      rooms.computeIfAbsent(chatKey, k -> ConcurrentHashMap.newKeySet()).add(session);
      sessionRoom.put(session, chatKey);
    }

    try {
      sendSystemMessage(session, "Connected as " + senderEmail);
    }
    catch (IOException ignored) {

    }

    try {
      processUnreadMessages(session, senderEmail);
    }
    catch (Exception e) {

      try { sendSystemMessage(session, "Unable to load unread messages right now."); } catch (IOException ignored) {}
    }

    if (receiverEmail != null && !receiverEmail.isBlank()) {
      try {
        if (userRepository.findByEmail(receiverEmail).isEmpty()) {
          sendSystemMessage(session, "Receiver not found.");
        }
        else if (!friendService.areFriends(senderEmail, receiverEmail)) {
          sendSystemMessage(session, "You can only chat with friends.");
        }
        else {
          try {
            processHistoryForReceiver(session, senderEmail, receiverEmail);
          }
          catch (Exception e) {
            sendSystemMessage(session, "Unable to load chat history right now.");
          }
        }
      }
      catch (Exception e) {
        try { sendSystemMessage(session, "Unable to validate receiver at the moment."); } catch (IOException ignored) {}
      }
    }
  }

  private void processUnreadMessages(WebSocketSession session, String senderEmail) throws Exception {
    List<Message> unread;
    try {
      unread = messageService.getUnreadMessages(senderEmail);
    }
    catch (Exception e) {
      sendSystemMessage(session, "Failed to retrieve unread messages.");
      return;
    }

    if (unread == null || unread.isEmpty()) return;

    List<Map<String, Object>> formatted = new ArrayList<>();
    for (Message message : unread) {
      try {
        if (shouldHideForRequester(message, senderEmail)) continue;
        Map<String, Object> payload = buildPayload(message);
        payload.put("optimistic", false);
        addPreviewIfFileQuiet(payload, message.getType(), message.getFileKey());
        formatted.add(payload);
      }
      catch (Exception ignored) {

      }
    }

    formatted.sort(Comparator.comparingLong(m -> ((Number) m.get("timestamp")).longValue()));

    Map<String, Object> unreadPayload = Map.of(
            "type", "unread",
            "count", formatted.size(),
            "messages", formatted);
    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(unreadPayload)));
  }

  private void processHistoryForReceiver(WebSocketSession session, String senderEmail, String receiverEmail) throws Exception {
    List<Message> dbMessages = Collections.emptyList();
    List<Message> pending = Collections.emptyList();

    try {
      dbMessages = messageService.getChat(senderEmail, receiverEmail);
      if (dbMessages == null) dbMessages = Collections.emptyList();
    }
    catch (Exception e) {

      dbMessages = Collections.emptyList();
    }

    try {
      pending = messageQueueService.getPendingForChat(senderEmail, receiverEmail);
      if (pending == null) pending = Collections.emptyList();
    }
    catch (Exception ignored) {
      pending = Collections.emptyList();
    }

    List<Message> filteredDb = dbMessages.stream()
            .filter(m -> !shouldHideForRequester(m, senderEmail))
            .filter(m -> m.getDeletedAt() == null)
            .collect(Collectors.toList());

    List<Message> filteredPending = pending.stream()
            .filter(m -> !shouldHideForRequester(m, senderEmail))
            .collect(Collectors.toList());

    List<Map<String, Object>> formatted = new ArrayList<>();

    for (Message message : filteredDb) {
      try {
        Map<String, Object> payload = buildPayload(message);
        payload.put("optimistic", false);
        addPreviewIfFileQuiet(payload, message.getType(), message.getFileKey());
        formatted.add(payload);
      }
      catch (Exception ignored) { }
    }

    for (Message message : filteredPending) {
      try {
        Map<String, Object> payload = buildPayload(message);
        payload.put("optimistic", true);
        addPreviewIfFileQuiet(payload, message.getType(), message.getFileKey());
        formatted.add(payload);
      }
      catch (Exception ignored) { }
    }

    formatted.sort(Comparator.comparingLong(m -> ((Number) m.get("timestamp")).longValue()));

    Map<String, Object> payload = Map.of(
            "type", "history",
            "chatWith", receiverEmail,
            "messages", formatted);
    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
  }

  private boolean shouldHideForRequester(Message m, String requesterEmail) {
    if (m == null || requesterEmail == null) return false;
    try {
      if (requesterEmail.equalsIgnoreCase(m.getSenderEmail()) && Boolean.TRUE.equals(m.getSenderDeleted())) {
        return true;
      }
      if (requesterEmail.equalsIgnoreCase(m.getReceiverEmail()) && Boolean.TRUE.equals(m.getReceiverDeleted())) {
        return true;
      }
      return m.getDeletedAt() != null;
    }
    catch (Exception e) {
      return false;
    }
  }

  private void sendChatSummary(WebSocketSession session, String senderEmail) throws Exception {
    List<String> partners;
    try {
      partners = messageService.getAllChatPartners(senderEmail);
    } catch (Exception e) {
      sendSystemMessage(session, "Unable to load chat partners.");
      return;
    }

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

  private void addPreviewIfFileQuiet(Map<String, Object> payload, String type, String fileKey) {
    if ("FILE".equalsIgnoreCase(type) && fileKey != null) {
      try {
        String previewUrl = s3Service.generatePresignedDownloadUrl(fileKey, Duration.ofMinutes(10));
        payload.put("previewUrl", previewUrl);
      } catch (Exception ignored) {
      }
    }
  }

  @Override
  protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage textMessage) throws Exception {
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
      case "FILE" -> {
        handleFileMessage(chatKey, senderEmail, clientPayload, session);
      }
      case "DELETE" -> {
        handleDeleteMessage(chatKey, senderEmail, clientPayload, session);
      }
      case "READ" -> handleReadMessages(clientPayload);
      case "SUMMARY" -> sendChatSummary(session, senderEmail);
      case "HISTORY" -> {
        Object cp = clientPayload.get("chatWith");
        if (cp instanceof String chatWith) {
          processHistoryForReceiver(session, senderEmail, chatWith);
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

    if (!friendService.areFriends(senderEmail, receiverEmail)) {
      sendSystemMessage(senderSession, "Cannot message non-friends.");
      return;
    }

    String messageUuid = UUID.randomUUID().toString();
    Message mess = Message.builder()
            .messageUuid(messageUuid)
            .senderEmail(senderEmail)
            .receiverEmail(receiverEmail)
            .content((String) payload.get("content"))
            .timestamp(LocalDateTime.now())
            .type("MESSAGE")
            .build();

    messageQueueService.enqueue(mess);

    Map<String, Object> sendPayload = buildPayload(mess);
    sendPayload.put("optimistic", true);
    sendToUsers(Set.of(senderEmail, receiverEmail), sendPayload);
  }

  private void handleFileMessage(String chatKey, String senderEmail, Map<String, Object> payload,
                                 WebSocketSession senderSession) throws IOException {
    String receiverEmail = deriveOtherFromChatKey(chatKey, senderEmail);
    if (receiverEmail == null) {
      sendSystemMessage(senderSession, "No chat partner specified.");
      return;
    }

    if (!friendService.areFriends(senderEmail, receiverEmail)) {
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
      } catch (Exception ignored) { }
    }

    String messageUuid = UUID.randomUUID().toString();
    Message mess = Message.builder()
            .messageUuid(messageUuid)
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
    if (previewUrl != null) payloadSend.put("previewUrl", previewUrl);
    payloadSend.put("optimistic", true);
    sendToUsers(Set.of(senderEmail, receiverEmail), payloadSend);
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
    boolean deleted = messageQueueService.deleteMessageByUuid(messageUuid);

    if (!deleted) {
      sendSystemMessage(senderSession, "Message not found or already deleted");
      return;
    }

    String receiverEmail = deriveOtherFromChatKey(chatKey, senderEmail);
    Map<String, Object> resp = Map.of(
            "type", "DELETE",
            "messageUuid", messageUuid,
            "deletedBy", senderEmail,
            "timestamp", Instant.now().toEpochMilli());

    if (receiverEmail != null) {
      sendToUsers(Set.of(senderEmail, receiverEmail), resp);
    }
    else {
      sendToUsers(Set.of(senderEmail), resp);
    }
  }

  private void sendToUsers(Set<String> emails, Map<String, Object> payload) throws IOException {
    if (emails == null || emails.isEmpty()) return;
    String json = objectMapper.writeValueAsString(payload);
    Set<WebSocketSession> targets = new LinkedHashSet<>();
    for (String email : emails) {
      Set<WebSocketSession> sessions = activeUsers.get(email.toLowerCase());
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

//  private void sendToReceiver(String email, Map<String, Object> payload) throws IOException {
//    WebSocketSession session = activeUsers.get(email);
//    if (session != null && session.isOpen()) {
//      session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
//    }
//  }


  private Map<String, Object> buildPayload(Message message) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("messageId", message.getId());
    payload.put("messageUuid", message.getMessageUuid());
    payload.put("type", message.getType());
    payload.put("senderEmail", message.getSenderEmail());
    payload.put("receiverEmail", message.getReceiverEmail());
    payload.put("content", message.getContent());

    long epochMillis;
    try {
      LocalDateTime ts = message.getTimestamp();
      if (ts != null) {
        epochMillis = ts.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
      }
      else {
        epochMillis = Instant.now().toEpochMilli();
      }
    }
    catch (Exception ignored) {
      epochMillis = Instant.now().toEpochMilli();
    }
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
    if (email == null) return null;
    return usernameCache.computeIfAbsent(email, e -> userRepository.findByEmail(e).map(User::getUsername).orElse(null));
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

  private String deriveOtherFromChatKey(String chatKey, String selfEmail) {
    if (chatKey == null || selfEmail == null) return null;
    String[] parts = chatKey.split("::", 2);
    if (parts.length != 2) return null;
    String a = parts[0];
    String b = parts[1];
    String lowerSelf = selfEmail.toLowerCase();
    if (lowerSelf.equals(a)) return b;
    if (lowerSelf.equals(b)) return a;
    return null;
  }

  @Override
  public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
    Map<String, String> meta = sessionMetadata.remove(session);
    String email = userSessions.remove(session);
    String room = sessionRoom.remove(session);

    if (email != null) {
      Set<WebSocketSession> userSet = activeUsers.get(email.toLowerCase());
      if (userSet != null) {
        userSet.remove(session);
        if (userSet.isEmpty()) activeUsers.remove(email.toLowerCase());
      }
    }

    if (room != null) {
      Set<WebSocketSession> set = rooms.getOrDefault(room, ConcurrentHashMap.newKeySet());
      set.remove(session);
      if (set.isEmpty()) rooms.remove(room);
    }
  }

  @Override
  public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) throws Exception {
    Map<String, String> meta = sessionMetadata.remove(session);
    String email = userSessions.remove(session);
    String room = sessionRoom.remove(session);

    if (email != null) {
      Set<WebSocketSession> userSet = activeUsers.get(email.toLowerCase());
      if (userSet != null) {
        userSet.remove(session);
        if (userSet.isEmpty()) activeUsers.remove(email.toLowerCase());
      }
    }

    if (room != null) {
      Set<WebSocketSession> set = rooms.getOrDefault(room, ConcurrentHashMap.newKeySet());
      set.remove(session);
      if (set.isEmpty()) rooms.remove(room);
    }

    if (session.isOpen()) {
      session.close(CloseStatus.SERVER_ERROR);
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
    if (session == null || !session.isOpen()) return;
    Map<String, Object> sys = Map.of(
            "type", "system",
            "system", content,
            "timestamp", Instant.now().toEpochMilli());
    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(sys)));
  }

//  public void broadcastMessageToUsers(Message message) {
//    try {
//      Map<String, Object> payload = buildPayload(message);
//      payload.put("optimistic", false);
//      sendToReceiver(message.getSenderEmail(), payload);
//      sendToReceiver(message.getReceiverEmail(), payload);
//    }
//    catch (Exception ignored) {
//
//    }
//  }

  public void broadcastMessageToUsers(Message message) {
    try {
      Map<String, Object> payload = buildPayload(message);
      payload.put("optimistic", false);
      sendToUsers(Set.of(message.getSenderEmail(), message.getReceiverEmail()), payload);
    }
    catch (Exception ignored) {
    }
  }

}
