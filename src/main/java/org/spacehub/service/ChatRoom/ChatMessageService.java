package org.spacehub.service.ChatRoom;

import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatMessageService {

    private final ChatMessageQueue chatMessageQueue;

    public ChatMessageService(ChatMessageQueue chatMessageQueue) {
        this.chatMessageQueue = chatMessageQueue;
    }

    public void sendMessage(ChatMessage message) {
        chatMessageQueue.addMessage(message);
    }

    public List<ChatMessage> getMessages(ChatRoom room) {
        return chatMessageQueue.getMessages(room);
    }

    public void flushAll() {
        chatMessageQueue.flushAllRooms();
    }

}
