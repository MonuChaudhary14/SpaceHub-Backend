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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageQueueService implements IMessageQueueService {

  private final Map<String, List<Message>> pendingByChat = new ConcurrentHashMap<>();

  private final IMessageService messageService;
  private ChatWebSocketHandlerMessaging messagingHandler;

  @Autowired
  @Lazy
  public void setMessagingHandler(ChatWebSocketHandlerMessaging handler) {
    this.messagingHandler = handler;
  }

  @Override
  public synchronized void enqueue(Message message) {
    String chatKey = buildChatKey(message.getSenderEmail(), message.getReceiverEmail());
    pendingByChat.computeIfAbsent(chatKey, k -> Collections.synchronizedList(new ArrayList<>())).add(message);
    List<Message> list = pendingByChat.get(chatKey);
    if (list != null && list.size() >= 10) {
      flushRoom(chatKey);
    }
  }

  @Scheduled(fixedRate = 2000)
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
      messageService.saveMessageBatch(batch);
    }
    catch (Exception e) {
      pendingByChat.computeIfAbsent(chatKey, k -> Collections.synchronizedList(new ArrayList<>())).addAll(batch);
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
            .flatMap(Collection::stream).anyMatch(m -> Objects.equals(m.getMessageUuid(), messageUuid));
  }

  private String buildChatKey(String a, String b) {
    if (a == null || b == null) return a + "::" + b;
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
