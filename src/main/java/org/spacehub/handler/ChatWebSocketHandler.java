package org.spacehub.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.ChatRoomUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.service.chatRoom.ChatMessageQueue;
import org.spacehub.service.ChatRoomService;
import org.spacehub.service.ChatRoomUserService;
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
  private final ChatMessageQueue chatMessageQueue;
  private final ChatRoomUserService chatRoomUserService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ChatWebSocketHandler(ChatRoomService chatRoomService,
                              ChatMessageQueue chatMessageQueue,
                              ChatRoomUserService chatRoomUserService) {
    this.chatRoomService = chatRoomService;
    this.chatMessageQueue = chatMessageQueue;
    this.chatRoomUserService = chatRoomUserService;
  }

  @Override
  public void afterConnectionEstablished(@NonNull WebSocketSession session) {
    try {
      String rawQuery = session.getUri() != null ? session.getUri().getQuery() : null;
      Map<String, String> params = parseQuery(rawQuery);

      String roomCodeStr = params.get("roomCode");
      String email = params.get("email");

      if (roomCodeStr == null || email == null) {
        logger.warn("Connection attempt missing roomCode or email. query={}", rawQuery);
        session.close(CloseStatus.BAD_DATA.withReason("roomCode and email required"));
        return;
      }

      UUID roomUuid;
      try {
        roomUuid = UUID.fromString(roomCodeStr);
      } catch (IllegalArgumentException ex) {
        logger.warn("Invalid roomCode format: {}", roomCodeStr);
        session.close(CloseStatus.BAD_DATA.withReason("Invalid roomCode"));
        return;
      }

      Optional<ChatRoom> optionalRoom = chatRoomService.findByRoomCode(roomUuid);
      if (optionalRoom.isEmpty()) {
        logger.warn("Room not found for code {}", roomUuid);
        session.close(new CloseStatus(4041, "Room not found"));
        return;
      }

      ChatRoom room = optionalRoom.get();

      List<ChatRoomUser> members = Collections.emptyList();
      try {
        members = chatRoomUserService.getMembersByRoomCode(roomUuid);
      }
      catch (Exception e) {
        logger.error("Error fetching members for room {}: {}", roomUuid, e.getMessage());
      }

      boolean isMember = members.stream().anyMatch(m -> m.getEmail() != null && m.getEmail().equalsIgnoreCase(email));

      if (!isMember) {
        try {
          chatRoomUserService.addUserToRoom(room, email, Role.MEMBER);
          logger.info("Auto-added user {} to room {}", email, roomUuid);
        }
        catch (Exception e) {
          logger.error("Failed to add user {} to room {}: {}", email, roomUuid, e.getMessage());
          session.close(CloseStatus.SERVER_ERROR.withReason("Failed to add member"));
          return;
        }
      }

      addSessionToRoom(session, roomUuid.toString(), email);

      try {
        sendExistingMessages(session, room);
      }
      catch (Exception e) {
        logger.warn("Failed to send history to {} for room {}: {}", email, roomUuid, e.getMessage());
      }

      broadcastSystemMessage(roomUuid.toString(), email + " joined the room");

      logger.info("WebSocket connected: user={} room={}", email, roomUuid);
    }
    catch (Exception top) {
      logger.error("Error in afterConnectionEstablished", top);
      try {
        session.close(CloseStatus.SERVER_ERROR.withReason("Internal server error while operating."));
      }
      catch (IOException ignored) {}
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

    String json;
    try {
      json = objectMapper.writeValueAsString(payload);
    }
    catch (Exception e) {
      logger.error("Error serializing message payload", e);
      return;
    }

    sessions.removeIf(s -> !s.isOpen());

    for (WebSocketSession session : sessions) {
      if (session.isOpen()) {
        try {
          session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
          logger.error("Error sending message", e);
        }
      }
    }
  }

  private Map<String, String> parseQuery(String query) {
    Map<String, String> params = new HashMap<>();
    if (query == null || query.isEmpty()) {
      return params;
    }
    try {
      String[] pairs = query.split("&");
      for (String pair : pairs) {
        String[] keyValue = pair.split("=", 2);
        if (keyValue.length == 2) {
          String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
          String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
          params.put(key, value);
        }
      }
    }
    catch (Exception e) {
      logger.error("Error parsing query params", e);
    }
    return params;
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

  private void sendExistingMessages(WebSocketSession session, ChatRoom room) {
    List<ChatMessage> messages = chatMessageQueue.getMessagesForRoom(room);

    for (ChatMessage message : messages) {
      Map<String, Object> payload = new HashMap<>();
      payload.put("senderEmail", message.getSenderEmail());
      payload.put("message", message.getMessage());
      payload.put("timestamp", message.getTimestamp());
      try {
        String json = objectMapper.writeValueAsString(payload);
        session.sendMessage(new TextMessage(json));
      } catch (Exception e) {
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
        if (sessions.isEmpty()) {
          rooms.remove(roomCode);
        }
      }
      broadcastSystemMessage(roomCode, email + " left the room");
    }

    logger.info("User {} disconnected from room {} (session: {})", email, roomCode, session.getId());
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
        String fileName = node.get("fileName").asText();
        String fileUrl = node.get("fileUrl").asText();
        String contentType = node.get("contentType").asText();

        Map<String, Object> filePayload = Map.of(
                "type", "FILE",
                "senderEmail", senderEmail,
                "fileName", fileName,
                "fileUrl", fileUrl,
                "contentType", contentType,
                "timestamp", System.currentTimeMillis()
        );

        broadcastJsonToRoom(roomCode, filePayload);
        return;
      }

      String messageText = node.has("message") ? node.get("message").asText() : payload;

      ChatMessage message = ChatMessage.builder()
              .senderEmail(senderEmail)
              .message(messageText)
              .timestamp(System.currentTimeMillis())
              .roomCode(roomCode)
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
