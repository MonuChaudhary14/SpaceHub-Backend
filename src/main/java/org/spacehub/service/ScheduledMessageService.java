package org.spacehub.service;

import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ScheduledMessage.ScheduledMessage;
import org.spacehub.repository.ScheduledMessageRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ScheduledMessageService {

    private final ScheduledMessageRepository scheduledMessageRepository;
    private final ChatMessageQueue chatMessageQueue;
    private final ChatRoomService chatRoomService;

    public ScheduledMessageService(ScheduledMessageRepository scheduledMessageRepository,
                                   ChatMessageQueue chatMessageQueue,
                                   ChatRoomService chatRoomService) {
        this.scheduledMessageRepository = scheduledMessageRepository;
        this.chatMessageQueue = chatMessageQueue;
        this.chatRoomService = chatRoomService;
    }

    public ScheduledMessage addScheduledMessage(ScheduledMessage message) {
        message.setSent(false);
        return scheduledMessageRepository.save(message);
    }

    @Scheduled(cron = "0 * * * * *")
    public void sendScheduledMessage() {
        List<ScheduledMessage> scheduled = scheduledMessageRepository.findBySentFalseAndScheduledTimeBefore(LocalDateTime.now());

        for (ScheduledMessage message : scheduled) {
            Optional<ChatRoom> optionalRoom = chatRoomService.findByRoomCode(message.getRoomCode());
            if (optionalRoom.isEmpty()) continue;

            ChatRoom room = optionalRoom.get();

            ChatMessage chatMessage = ChatMessage.builder()
                    .room(room)
                    .roomCode(room.getRoomCode())
                    .senderId(message.getSenderEmail())
                    .message(message.getMessage())
                    .timestamp(System.currentTimeMillis())
                    .build();

            chatMessageQueue.enqueue(chatMessage);

            message.setSent(true);
            scheduledMessageRepository.save(message);
        }
    }

}
