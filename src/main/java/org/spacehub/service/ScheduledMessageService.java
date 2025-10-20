package org.spacehub.service;

import org.spacehub.entities.ScheduledMessage;
import org.spacehub.repository.ScheduledMessageRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ScheduledMessageService {

    private final ScheduledMessageRepository scheduledMessageRepository;
    private final ChatMessageService chatMessageService;

    public ScheduledMessageService(ScheduledMessageRepository scheduledMessageRepository, ChatMessageService chatMessageService) {
        this.scheduledMessageRepository = scheduledMessageRepository;
        this.chatMessageService = chatMessageService;
    }

    public ScheduledMessage addScheduledMessage(ScheduledMessage message) {
        message.setSent(false);
        return scheduledMessageRepository.save(message);
    }

    @Scheduled(fixedRate = 60000)
    public void sendScheduledMessage() {
        List<ScheduledMessage> scheduled = scheduledMessageRepository.findPendingMessagesBefore(LocalDateTime.now());

        for (ScheduledMessage message : scheduled) {
            chatMessageService.sendMessage(
                    message.getRoomCode(),
                    message.getSenderEmail(),
                    message.getMessage()
            );
            message.setSent(true);
            scheduledMessageRepository.save(message);
        }
    }

}
