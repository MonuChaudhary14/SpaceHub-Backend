package org.spacehub.service.ChatRoom;

import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.repository.ChatRoom.ChatMessageRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatMessageQueue {

    private final ChatMessageRepository chatMessageRepository;

    private final Map<String, Deque<ChatMessage>> roomCache = new ConcurrentHashMap<>();
    private static final int CACHE_LIMIT = 100;
    private static final int BATCH_SIZE = 20;

    public ChatMessageQueue(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    public synchronized void addMessage(ChatMessage message) {
        String roomCode = message.getRoomCode();
        Deque<ChatMessage> messages = roomCache.computeIfAbsent(roomCode, k -> new LinkedList<>());

        messages.addLast(message);

        if (messages.size() >= BATCH_SIZE) {
            flushRoomMessages(roomCode);
        }

        while (messages.size() > CACHE_LIMIT) {
            messages.removeFirst();
        }
    }

    public synchronized List<ChatMessage> getMessages(ChatRoom room) {
        String roomCode = room.getRoomCode();
        Deque<ChatMessage> messages = roomCache.get(roomCode);

        if (messages == null || messages.isEmpty()) {
            List<ChatMessage> databaseMessages = chatMessageRepository.findByRoomOrderByTimestampAsc(room);
            roomCache.put(roomCode, new LinkedList<>(databaseMessages));
            return databaseMessages;
        }

        return new ArrayList<>(messages);
    }

    public synchronized void flushRoomMessages(String roomCode) {
        Deque<ChatMessage> messages = roomCache.get(roomCode);
        if (messages == null || messages.isEmpty()) return;

        chatMessageRepository.saveAll(new ArrayList<>(messages));
        messages.clear();
    }

    public synchronized void flushAllRooms() {
        for (String roomCode : roomCache.keySet()) {
            flushRoomMessages(roomCode);
        }
    }

}
