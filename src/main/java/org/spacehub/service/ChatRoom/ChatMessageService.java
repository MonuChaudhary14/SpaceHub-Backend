package org.spacehub.service.ChatRoom;

import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.repository.ChatRoom.ChatMessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;

    public ChatMessageService(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    public List<ChatMessage> saveAll(List<ChatMessage> messages) {
        return chatMessageRepository.saveAll(messages);
    }

    public List<ChatMessage> getMessagesForRoom(ChatRoom room) {
        return chatMessageRepository.findByRoomOrderByTimestampAsc(room);
    }

}
