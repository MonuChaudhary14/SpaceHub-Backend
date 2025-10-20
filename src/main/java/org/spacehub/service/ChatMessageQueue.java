package org.spacehub.service;

import org.spacehub.entities.ChatMessage;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class ChatMessageQueue {

    private final BlockingQueue<ChatMessage> queue = new LinkedBlockingQueue<>();
    private final int BATCH_SIZE = 10;

    private final ChatMessageService chatMessageService;

    public ChatMessageQueue(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    public synchronized void enqueue(ChatMessage message) {
        queue.add(message);

        if (queue.size() >= BATCH_SIZE) {
            addMessage();
        }
    }

    private synchronized void addMessage() {
        List<ChatMessage> batch = new ArrayList<>();
        queue.drainTo(batch);

        if (!batch.isEmpty()) {
            chatMessageService.saveAll(batch);
        }
    }

    @Scheduled(fixedRate = 60000)
    public void scheduledFlush() {
        addMessage();
    }

    public List<ChatMessage> getMessagesForRoom(org.spacehub.entities.ChatRoom room) {
        return chatMessageService.getMessagesForRoom(room);
    }

}
