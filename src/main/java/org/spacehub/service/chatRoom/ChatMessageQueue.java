package org.spacehub.service.chatRoom;

import lombok.RequiredArgsConstructor;
import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.NewChatRoom;
import org.spacehub.handler.ChatWebSocketHandler;
import org.spacehub.service.chatRoom.chatroomInterfaces.IChatMessageQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ChatMessageQueue implements IChatMessageQueue {

  private final Map<String, List<ChatMessage>> pendingByRoom = new ConcurrentHashMap<>();

  private final ChatMessageService chatMessageService;
  private ChatWebSocketHandler chatWebSocketHandler;

  private static final int FLUSH_BATCH_SIZE = 10;

  @Autowired
  @Lazy
  public void setChatWebSocketHandler(ChatWebSocketHandler handler) {
    this.chatWebSocketHandler = handler;
  }

  public synchronized void enqueue(ChatMessage message) {
    pendingByRoom.computeIfAbsent(message.getRoomCode(), k -> Collections.synchronizedList(new ArrayList<>()))
            .add(message);

    List<ChatMessage> list = pendingByRoom.get(message.getRoomCode());
    if (list != null && list.size() >= FLUSH_BATCH_SIZE) {
      flushRoom(message.getRoomCode());
    }
  }

  @Scheduled(fixedRate = 10000)
  public synchronized void flushQueue() {
    Set<String> rooms = new HashSet<>(pendingByRoom.keySet());
    for (String roomCode : rooms) {
      flushRoom(roomCode);
    }
  }

  private synchronized void flushRoom(String roomCode) {
    List<ChatMessage> pending = pendingByRoom.getOrDefault(roomCode, Collections.emptyList());
    if (pending.isEmpty()) return;

    List<ChatMessage> batch = new ArrayList<>(pending);
    pending.clear();
    pendingByRoom.remove(roomCode);

    try {
      chatMessageService.saveAll(batch);
    }
    catch (Exception e) {
      pendingByRoom.computeIfAbsent(roomCode, k -> Collections.synchronizedList(new ArrayList<>())).addAll(batch);
    }
  }

  public synchronized boolean deleteMessageByUuid(String messageUuid) {
    boolean removedFromMemory = pendingByRoom.values().stream()
            .anyMatch(list -> list.removeIf(m -> Objects.equals(m.getMessageUuid(), messageUuid)));

    boolean removedFromDb = chatMessageService.deleteMessageByUuid(messageUuid);

    return removedFromMemory || removedFromDb;
  }

  public List<ChatMessage> getMessagesForRoom(ChatRoom room) {
    return chatMessageService.getMessagesForRoom(room);
  }

  public List<ChatMessage> getMessagesForNewChatRoom(NewChatRoom newChatRoom) {
    List<ChatMessage> dbMessages = chatMessageService.getMessagesForNewChatRoom(newChatRoom);

    String roomCode = newChatRoom.getRoomCode().toString();
    List<ChatMessage> pending = pendingByRoom.getOrDefault(roomCode, Collections.emptyList());

    List<ChatMessage> combined = new ArrayList<>();
    combined.addAll(dbMessages);
    combined.addAll(new ArrayList<>(pending));

    combined.sort(Comparator.comparingLong(ChatMessage::getTimestamp));
    return combined;
  }

  public boolean isPending(String messageUuid) {
    return pendingByRoom.values().stream().anyMatch(list -> list.stream()
            .anyMatch(m -> Objects.equals(m.getMessageUuid(), messageUuid)));
  }

}
