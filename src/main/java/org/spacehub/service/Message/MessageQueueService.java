package org.spacehub.service.Message;

import lombok.RequiredArgsConstructor;
import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.handler.ChatWebSocketHandlerMessaging;
import org.spacehub.service.Interface.IMessageQueueService;
import org.spacehub.service.Interface.IMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class MessageQueueService implements IMessageQueueService {

  private final Map<String, List<Message>> pendingByChat = new ConcurrentHashMap<>();

  private final IMessageService messageService;

  private ChatWebSocketHandlerMessaging messagingHandler;

  private static final int FLUSH_BATCH_SIZE = 10;

  @Autowired
  @Lazy
  public void setMessagingHandler(ChatWebSocketHandlerMessaging handler) {
    this.messagingHandler = handler;
  }

  @Override
  public synchronized void enqueue(Message message) {
    if (message.getTimestamp() == null) {
      message.setTimestamp(java.time.Instant.now().toEpochMilli());
    }

    String chatKey = buildChatKey(message.getSenderEmail(), message.getReceiverEmail());
    pendingByChat.computeIfAbsent(chatKey, k -> Collections.synchronizedList(new ArrayList<>())).add(message);

    List<Message> list = pendingByChat.get(chatKey);
    if (list != null && list.size() >= FLUSH_BATCH_SIZE) {
      flushRoom(chatKey);
    }
  }

  @Scheduled(fixedRate = 5000)
  public synchronized void flushQueue() {
    List<String> chats = new ArrayList<>(pendingByChat.keySet());
    for (String chatKey : chats) {
      flushRoom(chatKey);
    }
  }

  private synchronized void flushRoom(String chatKey) {
    List<Message> pending = pendingByChat.getOrDefault(chatKey, Collections.emptyList());
    if (pending.isEmpty()) return;

    List<Message> batch = new ArrayList<>(pending);
    pending.clear();
    pendingByChat.remove(chatKey);

    try {
      List<Message> persisted = messageService.saveMessageBatch(batch);

      if (messagingHandler != null && persisted != null) {
        for (Message persistedMessage : persisted) {
          try {
            messagingHandler.broadcastMessageToUsers(persistedMessage);
          } catch (Exception ignored) {}
        }
      }
    }
    catch (Exception e) {
      pendingByChat.computeIfAbsent(chatKey, k -> Collections.synchronizedList(new ArrayList<>()))
              .addAll(batch);
    }
  }

  public synchronized boolean deleteMessageByUuid(String messageUuid) {
    boolean removedFromMemory = pendingByChat.values().stream()
            .anyMatch(list -> list.removeIf(m -> Objects.equals(m.getMessageUuid(), messageUuid)));

    boolean removedFromDb = messageService.deleteMessageByUuid(messageUuid);

    return removedFromMemory || removedFromDb;
  }

  public List<Message> getPendingForChat(String userA, String userB) {
    String chatKey = buildChatKey(userA, userB);
    List<Message> pending = pendingByChat.getOrDefault(chatKey, Collections.emptyList());
    return new ArrayList<>(pending);
  }

  public boolean isPending(String messageUuid) {
    return pendingByChat.values().stream()
            .flatMap(Collection::stream)
            .anyMatch(m -> Objects.equals(m.getMessageUuid(), messageUuid));
  }

  public String buildChatKey(String a, String b) {
    if (a == null || b == null) return (a == null ? "" : a.toLowerCase()) + "::" + (b == null ? "" : b.toLowerCase());
    String lowerA = a.toLowerCase();
    String lowerB = b.toLowerCase();
    if (lowerA.compareTo(lowerB) <= 0) {
      return lowerA + "::" + lowerB;
    }
    else {
      return lowerB + "::" + lowerA;
    }
  }
}
