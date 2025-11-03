package org.spacehub.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.ChatRoomUser;
import org.spacehub.service.chatRoom.ChatMessageQueue;
import org.spacehub.service.ChatRoomService;
import org.spacehub.service.ChatRoomUserService;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
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
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    String query = null;
    if (session.getUri() != null) {
      query = session.getUri().getQuery();
    }
    Map<String, String> params = parseQuery(query);

    String roomCode = params.get("roomCode");
    String email = params.get("email");

    if (!validateConnection(session, roomCode, email)) {
      return;
    }

    Optional<ChatRoom> optionalRoom = chatRoomService.findByRoomCode(UUID.fromString(roomCode));
    if (optionalRoom.isEmpty()) {
      session.close();
      return;
    }

    ChatRoom room = optionalRoom.get();
    if (!isUserMemberOfRoom(room, email)) {
      session.close();
      return;
    }

    addSessionToRoom(session, roomCode, email);
    sendExistingMessages(session, room);
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
      logger.error("Error sending message", e);
      return;
    }

    sessions.removeIf(s -> !s.isOpen());

    for (WebSocketSession session : sessions) {
      if (session.isOpen()) {
        try {
          session.sendMessage(new TextMessage(json));
        }
        catch (Exception e) {
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
          params.put(keyValue[0], keyValue[1]);
        }
      }
    } catch (Exception e) {
      logger.error("Error sending message", e);
    }

    return params;
  }

  private boolean validateConnection(WebSocketSession session, String roomCode, String email) throws Exception {
    if (roomCode == null || email == null) {
      session.close();
      return false;
    }
    return true;
  }

  private boolean isUserMemberOfRoom(ChatRoom room, String email) {
    List<ChatRoomUser> members = chatRoomUserService.getMembers(room);
    for (ChatRoomUser member : members) {
      if (member.getEmail().equals(email)) {
        return true;
      }
    }
    return false;
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
      payload.put("senderId", message.getSenderEmail());
      payload.put("message", message.getMessage());
      payload.put("timestamp", message.getTimestamp());
      try {
        String json = objectMapper.writeValueAsString(payload);
        session.sendMessage(new TextMessage(json));
      } catch (Exception e) {
        logger.error("Error sending message", e);
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
    }

    logger.info("User {} disconnected from room {} (session: {})", email, roomCode, session.getId());
  }

  protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage textMessage) throws Exception {
    String payload = textMessage.getPayload();
    String roomCode = sessionRoom.get(session);
    String senderEmail = userSessions.get(session);

    if (roomCode == null || senderEmail == null) {
      logger.warn("Received message but roomCode or senderEmail missing for session {}", session.getId());
      return;
    }

    String messageText;
    try {
      var node = objectMapper.readTree(payload);
      if (node.has("message")) messageText = node.get("message").asText();
      else messageText = node.asText();
    } catch (Exception e) {
      logger.warn("Invalid message payload: {}", payload);
      return;
    }

    Optional<ChatRoom> optionalRoom = chatRoomService.findByRoomCode(UUID.fromString(roomCode));
    if (optionalRoom.isEmpty()) {
      logger.warn("Room not found for code {}", roomCode);
      return;
    }
    ChatRoom room = optionalRoom.get();

    ChatMessage chatMsg = ChatMessage.builder()
      .senderEmail(senderEmail)
      .message(messageText)
      .timestamp(System.currentTimeMillis())
      .room(room)
      .roomCode(roomCode)
      .build();

    try {
      chatMessageQueue.enqueue(chatMsg);
    } catch (Exception e) {
      logger.error("Failed to enqueue chat message", e);
    }
  }

}
