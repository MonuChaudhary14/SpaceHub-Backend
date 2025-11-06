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
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    try {
      String rawQuery = session.getUri() != null ? session.getUri().getQuery() : null;
      Map<String, String> params = parseQuery(rawQuery);

      String newRoomCodeStr = params.get("roomCode");
      String email = params.get("email");

      if (newRoomCodeStr == null || email == null) {
        logger.warn("Connection attempt missing roomCode or email. query={}", rawQuery);
        session.close(CloseStatus.BAD_DATA.withReason("roomCode and email required"));
        return;
      }

      UUID newRoomUuid;
      try {
        newRoomUuid = UUID.fromString(newRoomCodeStr);
      }
      catch (IllegalArgumentException ex) {
        logger.warn("Invalid roomCode format: {}", newRoomCodeStr);
        session.close(CloseStatus.BAD_DATA.withReason("Invalid roomCode"));
        return;
      }

      Optional<NewChatRoom> optionalNewChatRoom = newChatRoomService.getEntityByCode(newRoomUuid);
      if (optionalNewChatRoom.isEmpty()) {
        logger.warn("NewChatRoom not found for code {}", newRoomUuid);
        session.close(new CloseStatus(4041, "Chat room not found"));
        return;
      }

      NewChatRoom newChatRoom = optionalNewChatRoom.get();
      ChatRoom parentGroup = newChatRoom.getChatRoom();

      List<ChatRoomUser> members = Collections.emptyList();
      try {
        members = chatRoomUserService.getMembersByRoomCode(parentGroup.getRoomCode());
      }
      catch (Exception e) {
        logger.error("Error fetching members for room {}: {}", parentGroup.getRoomCode(), e.getMessage());
      }

      boolean isMember = members.stream()
              .anyMatch(m -> m.getEmail() != null && m.getEmail().equalsIgnoreCase(email));

      if (!isMember) {
        try {
          chatRoomUserService.addUserToRoom(parentGroup, email, Role.MEMBER);
          logger.info("Auto-added user {} to parent ChatRoom {}", email, parentGroup.getRoomCode());
        }
        catch (Exception e) {
          logger.error("Failed to add user {} to ChatRoom {}: {}", email, parentGroup.getRoomCode(), e.getMessage());
          session.close(CloseStatus.SERVER_ERROR.withReason("Failed to add member"));
          return;
        }
      }

      addSessionToRoom(session, newRoomUuid.toString(), email);

      try {
        sendExistingMessages(session, newChatRoom);
      }
      catch (Exception e) {
        logger.warn("Failed to send history to {} for room {}: {}", email, newRoomUuid, e.getMessage());
      }

      broadcastSystemMessage(newRoomUuid.toString(), email + " joined the chat");
      logger.info("WebSocket connected: user={} newChatRoom={}", email, newRoomUuid);

    } catch (Exception top) {
      logger.error("Error in afterConnectionEstablished", top);
      try {
        session.close(CloseStatus.SERVER_ERROR.withReason("Internal server error while operating."));
      } catch (IOException ignored) {}
    }
  }


  public void broadcastMessageToRoom(ChatMessage message) {
    String roomCode = message.getRoomCode();
    Set<WebSocketSession> sessions = rooms.getOrDefault(roomCode, ConcurrentHashMap.newKeySet());

    Map<String, Object> payload = Map.of(
            "senderEmail", message.getSenderEmail(),
            "message", message.getMessage(),
            "timestamp", message.getTimestamp()
    );

    try {
      String json = objectMapper.writeValueAsString(payload);
      for (WebSocketSession s : sessions) {
        if (s.isOpen()) s.sendMessage(new TextMessage(json));
      }
    }
    catch (Exception e) {
      logger.error("Error sending message to room {}", roomCode, e);
    }
  }

  private Map<String, String> parseQuery(String query) {
    Map<String, String> params = new HashMap<>();
    if (query == null || query.isEmpty()) return params;
    try {
      for (String pair : query.split("&")) {
        String[] keyValue = pair.split("=", 2);
        if (keyValue.length == 2) {
          params.put(
                  URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8),
                  URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8)
          );
        }
      }
    }
    catch (Exception e) {
      logger.error("Error parsing query params", e);
    }
    return params;
  }

  private boolean validateConnection(WebSocketSession session, String roomCode, String email) throws Exception {
    if (roomCode == null || email == null) {
      session.close(CloseStatus.BAD_DATA);
      return false;
    }
    return true;
  }

  private boolean isUserMemberOfRoom(ChatRoom room, String email) {
    List<ChatRoomUser> members = chatRoomUserService.getMembers(room);
    if (members == null) return false;
    for (ChatRoomUser member : members) {
      if (member.getEmail() != null && member.getEmail().equalsIgnoreCase(email)) {
        return true;
      }
    }
    return false;
  }

  private void broadcastSystemMessage(String roomCode, String text) {
    ChatMessage msg = ChatMessage.builder()
            .senderEmail("system")
            .message(text)
            .timestamp(System.currentTimeMillis())
            .roomCode(roomCode)
            .build();
    broadcastMessageToRoom(msg);
  }

  private void addSessionToRoom(WebSocketSession session, String roomCode, String email) {
    rooms.computeIfAbsent(roomCode, k -> ConcurrentHashMap.newKeySet()).add(session);
    sessionRoom.put(session, roomCode);
    userSessions.put(session, email);
  }

  private void sendExistingMessages(WebSocketSession session, NewChatRoom newChatRoom) {
    List<ChatMessage> messages = chatMessageQueue.getMessagesForNewChatRoom(newChatRoom);

    for (ChatMessage message : messages) {
      Map<String, Object> payload = new HashMap<>();
      payload.put("senderEmail", message.getSenderEmail());
      payload.put("message", message.getMessage());
      payload.put("timestamp", message.getTimestamp());
      try {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
      }
      catch (Exception e) {
        logger.error("Error sending message history", e);
      }
    }
  }

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

    logger.info("User {} disconnected from NewChatRoom {} (session: {})", email, roomCode, session.getId());
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

      if ("FILE".equalsIgnoreCase(type)) {
        Map<String, Object> filePayload = Map.of(
                "type", "FILE",
                "senderEmail", senderEmail,
                "fileName", node.get("fileName").asText(),
                "fileUrl", node.get("fileUrl").asText(),
                "contentType", node.get("contentType").asText(),
                "timestamp", System.currentTimeMillis()
        );
        broadcastJsonToRoom(roomCode, filePayload);
        return;
      }

      Optional<NewChatRoom> optionalNewRoom = newChatRoomService.getEntityByCode(UUID.fromString(roomCode));
      if (optionalNewRoom.isEmpty()) {
        logger.warn("NewChatRoom not found for code {}", roomCode);
        return;
      }

      String messageText = node.has("message") ? node.get("message").asText() : payload;

      ChatMessage message = ChatMessage.builder()
              .senderEmail(senderEmail)
              .message(messageText)
              .timestamp(System.currentTimeMillis())
              .roomCode(roomCode)
              .newChatRoom(optionalNewRoom.get())
              .build();

      chatMessageQueue.enqueue(message);
      broadcastMessageToRoom(message);

    }
    catch (Exception e) {
      logger.error("Error processing WebSocket message", e);
    }
  }


  private void broadcastJsonToRoom(String roomCode, Map<String, Object> payload) {
    Set<WebSocketSession> sessions = rooms.getOrDefault(roomCode, ConcurrentHashMap.newKeySet());
    sessions.removeIf(s -> !s.isOpen());

    try {
      String json = objectMapper.writeValueAsString(payload);
      for (WebSocketSession s : sessions) {
        s.sendMessage(new TextMessage(json));
      }
    }
    catch (Exception e) {
      logger.error("Failed to broadcast file message", e);
    }
  }

}
