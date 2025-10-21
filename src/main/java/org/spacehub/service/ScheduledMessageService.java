package org.spacehub.service;

import org.spacehub.entities.ChatMessage;
import org.spacehub.entities.ScheduledMessage;
import org.spacehub.repository.ScheduledMessageRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ScheduledMessageService {

    private final ScheduledMessageRepository scheduledMessageRepository;
    private final ChatMessageQueue chatMessageQueue;

    public ScheduledMessageService(ScheduledMessageRepository scheduledMessageRepository,
                                   ChatMessageQueue chatMessageQueue) {
        this.scheduledMessageRepository = scheduledMessageRepository;
        this.chatMessageQueue = chatMessageQueue;
    }

    public ScheduledMessage addScheduledMessage(ScheduledMessage message) {
        message.setSent(false);
        return scheduledMessageRepository.save(message);
    }

    @Scheduled(fixedRate = 60000)
    public void sendScheduledMessage() {
        List<ScheduledMessage> scheduled = scheduledMessageRepository.findBySentFalseAndScheduledTimeBefore(LocalDateTime.now());

        for (ScheduledMessage message : scheduled) {
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setRoomCode(message.getRoomCode());
            chatMessage.setSenderId(message.getSenderEmail());
            chatMessage.setMessage(message.getMessage());
            chatMessage.setTimestamp(System.currentTimeMillis());

            chatMessageQueue.enqueue(chatMessage);

            message.setSent(true);
            scheduledMessageRepository.save(message);
        }
    }

}
