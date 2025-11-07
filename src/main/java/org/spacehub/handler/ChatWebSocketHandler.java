package org.spacehub.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.ChatRoomUser;
import org.spacehub.entities.ChatRoom.NewChatRoom;
import org.spacehub.entities.Community.Role;
import org.spacehub.service.chatRoom.ChatMessageQueue;
import org.spacehub.service.ChatRoomService;
import org.spacehub.service.ChatRoomUserService;
import org.spacehub.service.chatRoom.NewChatRoomService;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

  private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
  private final Map<WebSocketSession, String> sessionRoom = new ConcurrentHashMap<>();
  private final Map<WebSocketSession, String> userSessions = new ConcurrentHashMap<>();
  private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);

  private final ChatRoomService chatRoomService;
  private final NewChatRoomService newChatRoomService;
  private final ChatMessageQueue chatMessageQueue;
  private final ChatRoomUserService chatRoomUserService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ChatWebSocketHandler(ChatRoomService chatRoomService,
                              NewChatRoomService newChatRoomService,
                              ChatMessageQueue chatMessageQueue,
                              ChatRoomUserService chatRoomUserService) {
    this.chatRoomService = chatRoomService;
    this.newChatRoomService = newChatRoomService;
    this.chatMessageQueue = chatMessageQueue;
    this.chatRoomUserService = chatRoomUserService;
  }

  @Override
  public void afterConnectionEstablished(@NonNull WebSocketSession session) {
    try {
      Map<String, String> params = parseQuery(session.getUri() != null ? session.getUri().getQuery() : null);
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

      NewChatRoom newChatRoom = optRoom.get();
      ChatRoom parentRoom = newChatRoom.getChatRoom();

      List<ChatRoomUser> members = chatRoomUserService.getMembersByRoomCode(parentRoom.getRoomCode());
      boolean isMember = members.stream()
              .anyMatch(u -> email.equalsIgnoreCase(u.getEmail()));
      if (!isMember) chatRoomUserService.addUserToRoom(parentRoom, email, Role.MEMBER);

      addSessionToRoom(session, roomUUID.toString(), email);
      sendExistingMessages(session, newChatRoom);
      broadcastSystemMessage(roomUUID.toString(), email + " joined the chat");

      logger.info("WebSocket connected: {} -> {}", email, roomUUID);
    }
    catch (Exception e) {
      logger.error("Error establishing WebSocket connection", e);
      try {
        session.close(CloseStatus.SERVER_ERROR.withReason("Internal server error"));
      }
      catch (IOException ignored) {}
    }
  }


  public void broadcastMessageToRoom(ChatMessage message) {
    String roomCode = message.getRoomCode();

    Set<WebSocketSession> sessions = rooms.getOrDefault(roomCode, ConcurrentHashMap.newKeySet());

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", message.getFileUrl() != null ? "FILE" : "MESSAGE");
    payload.put("senderEmail", message.getSenderEmail());
    payload.put("message", message.getMessage());
    payload.put("timestamp", message.getTimestamp());
    if (message.getFileUrl() != null) {
      payload.put("fileName", message.getFileName());
      payload.put("fileUrl", message.getFileUrl());
      payload.put("contentType", message.getContentType());
    }

    try {
      String json = objectMapper.writeValueAsString(payload);
      for (WebSocketSession s : sessions) {
        if (s.isOpen()) s.sendMessage(new TextMessage(json));
      }
    } catch (Exception e) {
      logger.error("Error broadcasting message to room {}", roomCode, e);
    }
  }

  private Map<String, String> parseQuery(String query) {
    Map<String, String> params = new HashMap<>();
    if (query == null || query.isEmpty()) return params;
    for (String pair : query.split("&")) {
      String[] kv = pair.split("=", 2);
      if (kv.length == 2) {
        params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
      }
    }
    return params;
  }

  private void broadcastSystemMessage(String roomCode, String text) {
    ChatMessage systemMessage = ChatMessage.builder()
            .senderEmail("system")
            .message(text)
            .timestamp(System.currentTimeMillis())
            .roomCode(roomCode)
            .build();
    broadcastMessageToRoom(systemMessage);
  }

  private void addSessionToRoom(WebSocketSession session, String roomCode, String email) {
    rooms.computeIfAbsent(roomCode, k -> ConcurrentHashMap.newKeySet()).add(session);
    sessionRoom.put(session, roomCode);
    userSessions.put(session, email);
  }

  private void sendExistingMessages(WebSocketSession session, NewChatRoom newChatRoom) {
    List<ChatMessage> history = chatMessageQueue.getMessagesForNewChatRoom(newChatRoom);
    for (ChatMessage m : history) {

      Map<String, Object> payload = new LinkedHashMap<>();

      payload.put("type", m.getFileUrl() != null ? "FILE" : "MESSAGE");
      payload.put("senderEmail", m.getSenderEmail());
      payload.put("message", m.getMessage());
      payload.put("timestamp", m.getTimestamp());
      if (m.getFileUrl() != null) {
        payload.put("fileName", m.getFileName());
        payload.put("fileUrl", m.getFileUrl());
        payload.put("contentType", m.getContentType());
      }
      try {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
      }
      catch (IOException e) {
        logger.error("Error sending history", e);
      }
    }
  }

  @Override
  public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
    String roomCode = sessionRoom.remove(session);
    String email = userSessions.remove(session);

    if (roomCode != null) {
      Set<WebSocketSession> sessions = rooms.get(roomCode);
      if (sessions != null) {
        sessions.remove(session);
        if (sessions.isEmpty()) rooms.remove(roomCode);
      }
      broadcastSystemMessage(roomCode, email + " left the chat");
    }

    logger.info("User {} disconnected from room {}", email, roomCode);
  }

  @Override
  protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage textMessage) {
    String payload = textMessage.getPayload();
    String roomCode = sessionRoom.get(session);
    String senderEmail = userSessions.get(session);

    if (roomCode == null || senderEmail == null) {
      logger.warn("Missing roomCode or senderEmail for session {}", session.getId());
      return;
    }

    try {
      JsonNode node = objectMapper.readTree(payload);
      String type = node.has("type") ? node.get("type").asText() : "MESSAGE";
      Optional<NewChatRoom> optRoom = newChatRoomService.getEntityByCode(UUID.fromString(roomCode));
      if (optRoom.isEmpty()) return;
      NewChatRoom chatRoom = optRoom.get();

      if ("FILE".equalsIgnoreCase(type)) {
        ChatMessage fileMsg = ChatMessage.builder()
                .senderEmail(senderEmail)
                .message("[File]")
                .fileName(node.get("fileName").asText())
                .fileUrl(node.get("fileUrl").asText())
                .contentType(node.get("contentType").asText())
                .timestamp(System.currentTimeMillis())
                .roomCode(roomCode)
                .newChatRoom(chatRoom)
                .build();

        chatMessageQueue.enqueue(fileMsg);
        broadcastMessageToRoom(fileMsg);
        return;
      }

      String text = node.has("message") ? node.get("message").asText() : payload;
      ChatMessage message = ChatMessage.builder()
              .senderEmail(senderEmail)
              .message(text)
              .timestamp(System.currentTimeMillis())
              .roomCode(roomCode)
              .newChatRoom(chatRoom)
              .build();

      chatMessageQueue.enqueue(message);
      broadcastMessageToRoom(message);
    }
    catch (Exception e) {
      logger.error("Error processing WebSocket message", e);
    }
  }

}
