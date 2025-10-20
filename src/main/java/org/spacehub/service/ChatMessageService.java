package org.spacehub.service;

import org.spacehub.entities.ChatMessage;
import org.spacehub.entities.ChatRoom;
import org.spacehub.repository.ChatMessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;

    public ChatMessageService(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    public ChatMessage saveMessage(ChatMessage message) {
        return chatMessageRepository.save(message);
    }

    public List<ChatMessage> saveAll(List<ChatMessage> messages) {
        return chatMessageRepository.saveAll(messages);
    }

    public List<ChatMessage> getMessagesForRoom(ChatRoom room) {
        return chatMessageRepository.findByRoomOrderByTimestampAsc(room);
    }

    public void sendMessage(String roomCode, String senderEmail, String content) {
        ChatMessage message = new ChatMessage();
        message.setRoomCode(roomCode);
        message.setSenderEmail(senderEmail);
        message.setContent(content);
        chatMessageRepository.save(message);

        webSocketMessageSender.broadcastMessage(roomCode, message);
    }

}
