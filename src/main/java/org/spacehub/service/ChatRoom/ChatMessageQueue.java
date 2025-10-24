package org.spacehub.service.ChatRoom;

import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.Community.CommunityUser;
import org.spacehub.handler.ChatWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    public synchronized void enqueue(ChatMessage message) throws Exception {
        ChatRoom room = message.getRoom();
        String userId = message.getSenderId();

        Optional<CommunityUser> optionalCommunityUser = room.getCommunity().getCommunityUsers().stream()
                .filter(communityUser -> communityUser.getUser().getId().toString().equals(userId)).findFirst();

        if (optionalCommunityUser.isEmpty()) {
            throw new Exception("You are not a member of this community");
        }

        if (optionalCommunityUser.get().isBanned()) {
            throw new Exception("You are blocked in this community and cannot send messages");
        }

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
