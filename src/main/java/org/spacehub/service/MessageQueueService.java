package org.spacehub.service;

import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.service.Interface.IMessageService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MessageQueueService {

    private final List<Message> queue = new ArrayList<>();
    private final IMessageService messageService;

    public MessageQueueService(IMessageService messageService) {
        this.messageService = messageService;
    }

    public synchronized void enqueue(Message message) {
        queue.add(message);
        sendBatchIfSizeReached();
    }

    private synchronized void sendBatchIfSizeReached() {
        int BATCH_SIZE = 10;
        if (queue.size() >= BATCH_SIZE) flushQueue();
    }

    @Scheduled(cron = "0 * * * * *") // every minute
    public synchronized void sendEveryInterval() {
        flushQueue();
    }

    private synchronized void flushQueue() {
        if (queue.isEmpty()) return;

        List<Message> batch = new ArrayList<>(queue);
        queue.clear();

        try {
            messageService.saveMessageBatch(batch);
            System.out.println("Flushed " + batch.size() + " direct messages to DB.");
        }
        catch (Exception e) {
            e.printStackTrace();
            queue.addAll(batch);
        }
    }
}
