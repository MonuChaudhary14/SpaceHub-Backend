package org.spacehub.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.ChatRoomUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.service.ChatMessageQueue;
import org.spacehub.service.ChatRoomService;
import org.spacehub.service.ChatRoomUserService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.CloseStatus;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String userId = userSessions.get(session);
        String roomCode = sessionRoom.get(session);

        if (userId == null || roomCode == null) return;

        Optional<ChatRoom> OptionalRoom = chatRoomService.findByRoomCode(roomCode);
        if (OptionalRoom.isEmpty()) return;

        ChatRoom room = OptionalRoom.get();

        List<ChatRoomUser> members = chatRoomUserService.getMembers(room);

        boolean canSend = false;

        for (ChatRoomUser people : members) {
            if (people.getUserId().equals(userId)) {
                Role role = people.getRole();
                if (role == Role.ADMIN || role == Role.WORKSPACE_OWNER || role == Role.MEMBER) {
                    canSend = true;
                    break;
                }
            }
        }

        if (!canSend) return;

        ChatMessage chatMessage = ChatMessage.builder()
                .senderId(userId)
                .message(message.getPayload())
                .timestamp(System.currentTimeMillis())
                .room(room)
                .build();

        chatMessageQueue.enqueue(chatMessage);

        Map<String, Object> broadcast = Map.of(
                "senderId", userId,
                "message", message.getPayload(),
                "timestamp", chatMessage.getTimestamp()
        );

        String json;
        try {
            json = objectMapper.writeValueAsString(broadcast);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        Set<WebSocketSession> sessions = rooms.getOrDefault(roomCode, ConcurrentHashMap.newKeySet());
        sessions.removeIf(s -> !s.isOpen());

        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(new TextMessage(json));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomCode = sessionRoom.remove(session);
        String userId = userSessions.remove(session);

        if (roomCode != null && userId != null) {
            Optional<ChatRoom> roomOpt = chatRoomService.findByRoomCode(roomCode);
            roomOpt.ifPresent(room -> {
                chatRoomUserService.removeUserFromRoom(room, userId);
                Set<WebSocketSession> sessions = rooms.getOrDefault(roomCode, ConcurrentHashMap.newKeySet());
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    rooms.remove(roomCode);
                }
            });
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null) return map;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2) {
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                map.put(key, value);
            }
        }
        return map;
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


}
