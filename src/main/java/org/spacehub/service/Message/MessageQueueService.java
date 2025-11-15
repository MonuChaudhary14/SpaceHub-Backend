package org.spacehub.service.Message;

import lombok.RequiredArgsConstructor;
import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.handler.ChatWebSocketHandlerMessaging;
import org.spacehub.service.Interface.IMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class MessageQueueService {

  private final Map<String, List<Message>> pendingByChat = new ConcurrentHashMap<>();
  private final IMessageService messageService;
  private ChatWebSocketHandlerMessaging messagingHandler;
  private static final int FLUSH_BATCH_SIZE = 10;

  @Autowired
  @Lazy
  public void setMessagingHandler(ChatWebSocketHandlerMessaging handler) {
    this.messagingHandler = handler;
  }

  public synchronized void enqueue(Message message) {
    if (message.getTimestamp() == null) {
      message.setTimestamp(java.time.Instant.now().toEpochMilli());
    }
    if (message.getSenderEmail() != null) {
      message.setSenderEmail(message.getSenderEmail().trim().toLowerCase());
    }
    if (message.getReceiverEmail() != null) {
      message.setReceiverEmail(message.getReceiverEmail().trim().toLowerCase());
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
    Set<String> chats = new HashSet<>(pendingByChat.keySet());
    for (String chatKey : chats) {
      flushRoom(chatKey);
    }
  }

  private synchronized void flushRoom(String chatKey) {
    List<Message> pending = pendingByChat.getOrDefault(chatKey, Collections.emptyList());
    if (pending.isEmpty()) {
      return;
    }
    List<Message> batch = new ArrayList<>(pending);
    pending.clear();
    pendingByChat.remove(chatKey);
    try {
      List<Message> persisted = messageService.saveMessageBatch(batch);
      if (messagingHandler != null && persisted != null) {
        for (Message persistedMessage : persisted) {
          try {
            messagingHandler.confirmAndBroadcast(persistedMessage);
          } catch (Exception ignored) {}
        }
      }
    } catch (Exception e) {
      pendingByChat.computeIfAbsent(chatKey, k ->
        Collections.synchronizedList(new ArrayList<>())).addAll(batch);
    }
  }

  public synchronized boolean deleteMessageByUuid(String messageUuid) {
    boolean removedFromMemory =
      pendingByChat.values().stream().anyMatch(list ->
        list.removeIf(m -> Objects.equals(m.getMessageUuid(), messageUuid)));
    boolean removedFromDb = messageService.deleteMessageByUuid(messageUuid);
    return removedFromMemory || removedFromDb;
  }

  public List<Message> getPendingForChat(String userA, String userB) {
    String chatKey = buildChatKey(userA, userB);
    List<Message> pending = pendingByChat.getOrDefault(chatKey, Collections.emptyList());
    return new ArrayList<>(pending);
  }

  public boolean isPending(String messageUuid) {
    return pendingByChat.values().stream().flatMap(Collection::stream).anyMatch(m ->
      Objects.equals(m.getMessageUuid(), messageUuid));
  }

  public String buildChatKey(String a, String b) {
    String aa = Optional.ofNullable(a)
      .map(s -> s.trim().toLowerCase())
      .orElse("");
    String bb = Optional.ofNullable(b)
      .map(s -> s.trim().toLowerCase())
      .orElse("");
    if (aa.compareTo(bb) <= 0) {
      return aa + "::" + bb;
    }
    return bb + "::" + aa;
  }

}
