package org.spacehub.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.ChatRoomUser;
import org.spacehub.service.ChatMessageQueue;
import org.spacehub.service.ChatRoomService;
import org.spacehub.service.ChatRoomUserService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

  private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
  private final Map<WebSocketSession, String> sessionRoom = new ConcurrentHashMap<>();
  private final Map<WebSocketSession, String> userSessions = new ConcurrentHashMap<>();

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
    String query = session.getUri() != null ? session.getUri().getQuery() : null;
    Map<String, String> params = parseQuery(query);

    String roomCode = params.get("roomCode");
    String userId = params.get("userId");

    if (roomCode == null || userId == null) {
      session.close();
      return;
    }

    Optional<ChatRoom> OptionalRoom = chatRoomService.findByRoomCode(roomCode);
    if (OptionalRoom.isEmpty()) {
      session.close();
      return;
    }

    ChatRoom room = OptionalRoom.get();

    List<ChatRoomUser> members = chatRoomUserService.getMembers(room);
    boolean isMember = false;

    for (ChatRoomUser member : members) {
      if (member.getUserId().equals(userId)) {
        isMember = true;
        break;
      }
    }
    if (!isMember) {
      session.close();
      return;
    }

    rooms.computeIfAbsent(roomCode, k -> ConcurrentHashMap.newKeySet()).add(session);
    sessionRoom.put(session, roomCode);
    userSessions.put(session, userId);

    List<ChatMessage> messages = chatMessageQueue.getMessagesForRoom(room);

    for (ChatMessage message : messages) {
      Map<String, Object> payload = new HashMap<>();
      payload.put("senderId", message.getSenderId());
      payload.put("message", message.getMessage());
      payload.put("timestamp", message.getTimestamp());

      try {
        String json = objectMapper.writeValueAsString(payload);
        session.sendMessage(new TextMessage(json));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public void broadcastMessageToRoom(ChatMessage message) {
    String roomCode = message.getRoomCode();
    Set<WebSocketSession> sessions = rooms.getOrDefault(roomCode, ConcurrentHashMap.newKeySet());

    Map<String, Object> payload = Map.of(
      "senderId", message.getSenderId(),
      "message", message.getMessage(),
      "timestamp", message.getTimestamp()
    );

    String json;
    try {
      json = objectMapper.writeValueAsString(payload);
    }
    catch (Exception e) {
      e.printStackTrace();
      return;
    }

    sessions.removeIf(s -> !s.isOpen());

    for (WebSocketSession session : sessions) {
      if (session.isOpen()) {
        try {
          session.sendMessage(new TextMessage(json));
        }
        catch (Exception e) {
          e.printStackTrace();
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
      e.printStackTrace();
    }

    return params;
  }
}
