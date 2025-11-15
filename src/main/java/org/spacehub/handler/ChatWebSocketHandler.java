package org.spacehub.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.NewChatRoom;
import org.spacehub.entities.User.User;
import org.spacehub.repository.User.UserRepository;
import org.spacehub.service.File.S3Service;
import org.spacehub.service.chatRoom.ChatMessageQueue;
import org.spacehub.service.chatRoom.NewChatRoomService;
import org.spacehub.utils.S3PreviewHelper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

  private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);

  private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
  private final Map<WebSocketSession, String> sessionRoom = new ConcurrentHashMap<>();
  private final Map<WebSocketSession, String> userSessions = new ConcurrentHashMap<>();

  private final NewChatRoomService newChatRoomService;
  private final ChatMessageQueue chatMessageQueue;
  private final S3Service s3Service;
  private final UserRepository userRepository;
  private final ObjectMapper objectMapper;

  public ChatWebSocketHandler(NewChatRoomService newChatRoomService,
                              ChatMessageQueue chatMessageQueue,
                              S3Service s3Service,
                              UserRepository userRepository) {
    this.newChatRoomService = newChatRoomService;
    this.chatMessageQueue = chatMessageQueue;
    this.s3Service = s3Service;
    this.userRepository = userRepository;
    this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  @Override
  public void afterConnectionEstablished(@NonNull WebSocketSession session) {
    try {
      Map<String, String> params = parseQuery(
        Optional.ofNullable(session.getUri())
          .map(URI::getQuery)
          .orElse(null)
      );
      String roomCodeStr = params.get("roomCode");
      String email = params.get("email");

      if (roomCodeStr == null || email == null) {
        session.close(CloseStatus.BAD_DATA.withReason("roomCode and email required"));
        return;
      }

      UUID roomUUID = UUID.fromString(roomCodeStr);
      Optional<NewChatRoom> optRoom = newChatRoomService.getEntityByCode(roomUUID);
      if (optRoom.isEmpty()) {
        session.close(new CloseStatus(4041, "Chat room not found"));
        return;
      }

      addSessionToRoom(session, roomUUID.toString(), email);
      sendExistingMessages(session, optRoom.get());
      broadcastSystemMessage(roomUUID.toString(), email + " joined the chat");

      logger.info("Connected: {} -> {}", email, roomUUID);
    }
    catch (Exception e) {
      logger.error("Error establishing WebSocket connection", e);
      try {
        session.close(CloseStatus.SERVER_ERROR.withReason("Internal server error"));
      } catch (IOException ignored) {}
    }
  }

//  public void broadcastMessageToRoom(ChatMessage message) {
//    try {
//      Map<String, Object> payload = buildMessagePayload(message);
//      payload.put("optimistic", false);
//      broadcastToRoom(message.getRoomCode(), payload);
//    }
//    catch (Exception e) {
//      logger.error("Error broadcasting message to room {}", message.getRoomCode(), e);
//    }
//  }

  private Map<String, String> parseQuery(String query) {
    Map<String, String> map = new HashMap<>();
    if (query == null || query.isBlank()) return map;
    for (String pair : query.split("&")) {
      String[] kv = pair.split("=", 2);
      if (kv.length == 2) {
        map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
      }
    }
    return map;
  }

  private void broadcastSystemMessage(String roomCode, String text) {
    Map<String, Object> system = Map.of("type", "SYSTEM", "message", text, "timestamp",
      Instant.now().toEpochMilli());
    try {
      broadcastToRoom(roomCode, system);
    }
    catch (IOException ignored) {

    }
  }

  private void addSessionToRoom(WebSocketSession session, String roomCode, String email) {
    rooms.computeIfAbsent(roomCode, k -> ConcurrentHashMap.newKeySet()).add(session);
    sessionRoom.put(session, roomCode);
    userSessions.put(session, email);
  }

  private void sendExistingMessages(WebSocketSession session, NewChatRoom newChatRoom) {
    List<ChatMessage> history = chatMessageQueue.getMessagesForNewChatRoom(newChatRoom);
    history.sort(Comparator.comparingLong(ChatMessage::getTimestamp));

    try {
      List<Map<String, Object>> formatted = new ArrayList<>();
      for (ChatMessage message : history) {
        Map<String, Object> payload = buildMessagePayload(message);
        boolean inMemory = chatMessageQueue.isPending(message.getMessageUuid());
        payload.put("optimistic", inMemory);
        formatted.add(payload);
      }
      Map<String, Object> response = Map.of(
              "type", "history",
              "roomCode", newChatRoom.getRoomCode(),
              "messages", formatted);
      session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }
    catch (IOException e) {
      logger.error("Error sending chat history", e);
    }
  }

  @Override
  public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
    String roomCode = sessionRoom.remove(session);
    String email = userSessions.remove(session);
    if (roomCode != null) {
      Set<WebSocketSession> set = rooms.getOrDefault(roomCode, ConcurrentHashMap.newKeySet());
      set.remove(session);
      if (set.isEmpty()) rooms.remove(roomCode);
      broadcastSystemMessage(roomCode, email + " left the chat");
    }
  }

  @Override
  protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage textMessage) {
    try {
      Map<String, Object> clientPayload = objectMapper.readValue(textMessage.getPayload(), new TypeReference<>() {});
      String type = ((String) clientPayload.getOrDefault("type", "MESSAGE")).toUpperCase();
      String roomCode = sessionRoom.get(session);
      String senderEmail = userSessions.get(session);

      if (roomCode == null || senderEmail == null) {
        sendSystemMessage(session, "Session not associated with a room or email");
        return;
      }

      switch (type) {
        case "FILE" -> handleFileMessage(roomCode, senderEmail, clientPayload, session);
        case "DELETE" -> handleDeleteMessage(roomCode, senderEmail, clientPayload);
        default -> handleTextMessage(roomCode, senderEmail, clientPayload, session);
      }
    }
    catch (Exception e) {
      logger.error("Error handling WebSocket message", e);
    }
  }

  private void handleTextMessage(String roomCode, String senderEmail, Map<String, Object> payload,
                                 WebSocketSession senderSession) throws IOException {
    var optionalRoom = newChatRoomService.getEntityByCode(UUID.fromString(roomCode));
    if (optionalRoom.isEmpty()) {
      sendSystemMessage(senderSession, "Chat room not found");
      return;
    }

    String messageUuid = UUID.randomUUID().toString();
    ChatMessage message = ChatMessage.builder()
            .messageUuid(messageUuid)
            .senderEmail(senderEmail)
            .message((String) payload.get("message"))
            .timestamp(Instant.now().toEpochMilli())
            .roomCode(roomCode)
            .newChatRoom(optionalRoom.get())
            .type("MESSAGE")
            .build();

    chatMessageQueue.enqueue(message);

    Map<String, Object> messagePayload = buildMessagePayload(message);
    messagePayload.put("optimistic", true);
    broadcastToRoom(roomCode, messagePayload);
  }

  private void handleFileMessage(String roomCode, String senderEmail, Map<String, Object> payload,
                                 WebSocketSession senderSession) throws IOException {
    var optionalRoom = newChatRoomService.getEntityByCode(UUID.fromString(roomCode));
    if (optionalRoom.isEmpty()) {
      sendSystemMessage(senderSession, "Chat room not found");
      return;
    }

    String messageUuid = UUID.randomUUID().toString();
    String fileKey = (String) payload.get("fileKey");
    String fileName = (String) payload.get("fileName");
    String contentType = (String) payload.get("contentType");

    String previewUrl = S3PreviewHelper.generatePreviewUrlQuietly(s3Service, fileKey, Duration.ofMinutes(15));

    ChatMessage message = ChatMessage.builder()
            .messageUuid(messageUuid)
            .senderEmail(senderEmail)
            .message("[File] " + fileName)
            .fileName(fileName)
            .fileUrl(previewUrl)
            .contentType(contentType)
            .timestamp(Instant.now().toEpochMilli())
            .roomCode(roomCode)
            .newChatRoom(optionalRoom.get())
            .type("FILE")
            .build();

    chatMessageQueue.enqueue(message);

    Map<String, Object> messagePayload = buildMessagePayload(message);
    messagePayload.put("optimistic", true);
    broadcastToRoom(roomCode, messagePayload);
  }

  private void handleDeleteMessage(String roomCode, String senderEmail, Map<String, Object> payload)
    throws IOException {
    Object uuidObj = payload.get("messageUuid");
    if (uuidObj == null) {
      sendSystemMessage(findSessionFor(roomCode, senderEmail), "Missing messageUuid for DELETE action");
      return;
    }

    String messageUuid = uuidObj.toString();

    boolean deleted = chatMessageQueue.deleteMessageByUuid(messageUuid);
    if (!deleted) {
      sendSystemMessage(findSessionFor(roomCode, senderEmail), "Message not found or already deleted");
      return;
    }

    Map<String, Object> deletePayload = Map.of(
            "type", "DELETE",
            "messageUuid", messageUuid,
            "deletedBy", senderEmail,
            "timestamp", Instant.now().toEpochMilli());
    broadcastToRoom(roomCode, deletePayload);
  }

  private WebSocketSession findSessionFor(String roomCode, String email) {
    return sessionRoom.entrySet().stream()
            .filter(e -> roomCode.equals(e.getValue()) &&
              email.equals(userSessions.get(e.getKey())))
            .map(Map.Entry::getKey).findFirst().orElse(null);
  }

  private Map<String, Object> buildMessagePayload(ChatMessage message) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("messageUuid", message.getMessageUuid());
    payload.put("type", message.getType());
    payload.put("senderEmail", message.getSenderEmail());
    payload.put("message", message.getMessage());
    payload.put("timestamp", message.getTimestamp());
    payload.put("fileName", message.getFileName());
    payload.put("fileUrl", message.getFileUrl());
    payload.put("contentType", message.getContentType());

    try {
      Optional<User> sUser = userRepository.findByEmail(message.getSenderEmail());
      payload.put("senderUsername", sUser.map(User::getUsername).orElse(null));
    }
    catch (Exception ignored) {
      payload.put("senderUsername", null);
    }
    return payload;
  }

  private void broadcastToRoom(String roomCode, Map<String, Object> payload) throws IOException {
    Set<WebSocketSession> sessions = rooms.getOrDefault(roomCode, Collections.emptySet());
    String json = objectMapper.writeValueAsString(payload);
    for (WebSocketSession s : sessions) {
      if (s != null && s.isOpen()) {
        s.sendMessage(new TextMessage(json));
      }
    }
  }

  private void sendSystemMessage(WebSocketSession session, String content) throws IOException {
    if (session == null) return;
    Map<String, Object> sys = Map.of("type", "system", "system", content, "timestamp",
      Instant.now().toEpochMilli());
    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(sys)));
  }

}
