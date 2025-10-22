package org.spacehub.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatPoll;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.ChatRoomUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.service.ChatRoom.ChatMessageQueue;
import org.spacehub.service.ChatRoom.ChatPollService;
import org.spacehub.service.ChatRoom.ChatRoomService;
import org.spacehub.service.ChatRoom.ChatRoomUserService;
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
    private final ChatPollService chatPollService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatWebSocketHandler(ChatRoomService chatRoomService,
                                ChatMessageQueue chatMessageQueue,
                                ChatRoomUserService chatRoomUserService,
                                ChatPollService chatPollService) {
        this.chatRoomService = chatRoomService;
        this.chatMessageQueue = chatMessageQueue;
        this.chatRoomUserService = chatRoomUserService;
        this.chatPollService = chatPollService;
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

        for (ChatMessage message : chatMessageQueue.getMessagesForRoom(room)) {
            sendJson(session, Map.of(
                    "type", "message",
                    "senderId", message.getSenderId(),
                    "message", message.getMessage(),
                    "timestamp", message.getTimestamp()
            ));
        }

        for (ChatPoll poll : chatPollService.getPollsForRoom(roomCode)) {
            sendJson(session, Map.of(
                    "type", "poll",
                    "pollId", poll.getId(),
                    "question", poll.getQuestion(),
                    "options", poll.getOptions(),
                    "timestamp", poll.getTimestamp()
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

        switch (type) {
            case "message" -> handleChatMessage(userId, room, (String) payload.get("message"));
            case "poll" -> handlePollCreation(userId, roomCode, payload);
            case "vote" -> handleVote(userId, roomCode, payload);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomCode = sessionRoom.remove(session);
        String userId = userSessions.remove(session);

        if (roomCode != null && userId != null) {
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
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                map.put(key, value);
            }
        }
        return map;
    }

    private void handleChatMessage(String userId, ChatRoom room, String messageText) {

        boolean canSend = chatRoomUserService.getMembers(room).stream().anyMatch(member -> member.getUserId().equals(userId) &&
                        (member.getRole() == Role.ADMIN || member.getRole() == Role.WORKSPACE_OWNER || member.getRole() == Role.MEMBER));

        if (!canSend) return;

        ChatMessage chatMessage = ChatMessage.builder()
                .senderId(userId)
                .message(messageText)
                .timestamp(System.currentTimeMillis())
                .room(room)
                .build();

        chatMessageQueue.enqueue(chatMessage);

        broadcastToRoom(room.getRoomCode(), Map.of(
                "type", "message",
                "senderId", userId,
                "message", messageText,
                "timestamp", chatMessage.getTimestamp()
        ));
    }

    private void handlePollCreation(String userId, String roomCode, Map<String, Object> message) {
        try {
            ChatPoll poll = chatPollService.createPoll(roomCode, userId, message);
            broadcastToRoom(roomCode, Map.of(
                    "type", "poll",
                    "pollId", poll.getId(),
                    "question", poll.getQuestion(),
                    "options", poll.getOptions(),
                    "timestamp", poll.getTimestamp()
            ));
        }
        catch (RuntimeException ignored) {
        }
    }

    private void handleVote(String userId, String roomCode, Map<String, Object> message) {
        try {
            chatPollService.vote(roomCode, userId, message);
            broadcastToRoom(roomCode, Map.of(
                    "type", "vote",
                    "pollId", message.get("pollId"),
                    "userId", userId,
                    "optionIndex", message.get("optionIndex")
            ));
        }
        catch (RuntimeException ignored) {
        }
    }

    private void broadcastToRoom(String roomCode, Map<String, Object> data) {
        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        Set<WebSocketSession> sessions = rooms.getOrDefault(roomCode, ConcurrentHashMap.newKeySet());
        sessions.removeIf(session -> !session.isOpen());

        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) session.sendMessage(new TextMessage(json));
            } catch (Exception e) {
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

    public void broadcastMessageToRoom(ChatMessage message) {
        broadcastToRoom(message.getRoom().getRoomCode(), Map.of(
                "type", "message",
                "senderId", message.getSenderId(),
                "message", message.getMessage(),
                "timestamp", message.getTimestamp()
        ));
    }

}
