package org.spacehub.service.ChatRoom;

import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.handler.ChatWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChatMessageQueue {

    private final List<ChatMessage> queue = new ArrayList<>();
    private final int BATCH_SIZE = 10;

    private final ChatMessageService chatMessageService;
    private ChatWebSocketHandler chatWebSocketHandler;

    @Autowired
    public ChatMessageQueue(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    @Autowired
    @Lazy
    public void setChatWebSocketHandler(ChatWebSocketHandler chatWebSocketHandler) {
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    public synchronized void enqueue(ChatMessage message) {
        queue.add(message);
        sendBatchIfSizeReached();
    }

    private synchronized void sendBatchIfSizeReached() {
        if (queue.size() >= BATCH_SIZE) {
            flushQueue();
        }
    }

    @Scheduled(cron = "0 * * * * *")
    public synchronized void sendEveryInterval() {
        flushQueue();
    }

    private synchronized void flushQueue() {
        if (queue.isEmpty()) return;

        List<ChatMessage> batch = new ArrayList<>(queue);
        queue.clear();

        chatMessageService.saveAll(batch);

        for (ChatMessage message : batch) {
            try {
                chatWebSocketHandler.broadcastMessageToRoom(message);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public List<ChatMessage> getMessagesForRoom(ChatRoom room) {
        return chatMessageService.getMessagesForRoom(room);
    }

}
