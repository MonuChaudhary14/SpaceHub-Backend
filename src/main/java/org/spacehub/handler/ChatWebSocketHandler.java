package org.spacehub.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatPoll;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.ChatRoomUser;
import org.spacehub.entities.Community.CommunityUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.service.ChatRoom.*;
import org.spacehub.service.community.CommunityService;
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
    private final ChatMessageService chatMessageService;
    private final ChatRoomUserService chatRoomUserService;
    private final CommunityService communityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatWebSocketHandler(ChatRoomService chatRoomService,
                                ChatMessageService chatMessageService,
                                ChatRoomUserService chatRoomUserService,
                                CommunityService communityService) {
        this.chatRoomService = chatRoomService;
        this.chatMessageService = chatMessageService;
        this.chatRoomUserService = chatRoomUserService;
        this.communityService = communityService;
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

        Optional<ChatRoom> optionalRoom = chatRoomService.findByRoomCode(roomCode);
        if (optionalRoom.isEmpty()) {
            session.close();
            return;
        }

        ChatRoom room = optionalRoom.get();

        List<CommunityUser> communityMembers = (List<CommunityUser>) communityService.getCommunityMembers(room.getCommunity().getId()).getBody();
        if (communityMembers == null) communityMembers = List.of();

        boolean isBannedOrBlocked = communityMembers.stream().anyMatch(member -> member.getUser().getId().toString().equals(userId) &&
                        (member.isBanned() || member.isBlocked()));

        if (isBannedOrBlocked) {
            session.sendMessage(new TextMessage("{\"error\":\"You are banned or blocked from this room.\"}"));
            session.close();
            return;
        }

        rooms.computeIfAbsent(roomCode, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionRoom.put(session, roomCode);
        userSessions.put(session, userId);

        List<ChatMessage> history = chatMessageService.getMessages(room);
        for (ChatMessage message : history) {
            sendJson(session, Map.of(
                    "type", "message",
                    "senderId", message.getSenderId(),
                    "message", message.getMessage(),
                    "timestamp", message.getTimestamp()
            ));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = userSessions.get(session);
        String roomCode = sessionRoom.get(session);

        if (userId == null || roomCode == null) return;

        Optional<ChatRoom> OptionalRoom = chatRoomService.findByRoomCode(roomCode);
        if (OptionalRoom.isEmpty()) return;

        ChatRoom room = OptionalRoom.get();

        Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
        String type = (String) payload.get("type");

        if ("message".equals(type)) {
            String messageData = (String) payload.get("message");
            ChatMessage chatMessage = ChatMessage.builder()
                    .senderId(userId)
                    .message(messageData)
                    .timestamp(System.currentTimeMillis())
                    .room(room)
                    .roomCode(roomCode)
                    .build();

            chatMessageService.sendMessage(chatMessage);

            broadcastToRoom(roomCode, Map.of(
                    "type", "message",
                    "senderId", userId,
                    "message", messageData,
                    "timestamp", chatMessage.getTimestamp()
            ));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomCode = sessionRoom.remove(session);
        String userId = userSessions.remove(session);

        if (roomCode != null) {
            Set<WebSocketSession> sessions = rooms.getOrDefault(roomCode, ConcurrentHashMap.newKeySet());
            sessions.remove(session);
            if (sessions.isEmpty()) rooms.remove(roomCode);
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null) return map;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2) {
                map.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private void broadcastToRoom(String roomCode, Map<String, Object> data) {
        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        }
        catch (Exception e) {
            e.printStackTrace();
            return;
        }

        Set<WebSocketSession> sessions = rooms.getOrDefault(roomCode, ConcurrentHashMap.newKeySet());
        sessions.removeIf(s -> !s.isOpen());

        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) session.sendMessage(new TextMessage(json));
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendJson(WebSocketSession session, Map<String, Object> data) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(data)));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
