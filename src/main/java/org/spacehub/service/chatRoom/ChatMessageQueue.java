package org.spacehub.service.chatRoom;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.NewChatRoom;
import org.spacehub.service.chatRoom.chatroomInterfaces.IChatMessageQueue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageQueue implements IChatMessageQueue {

  private static final Logger logger = LoggerFactory.getLogger(ChatMessageQueue.class);
  private static final int FLUSH_BATCH_SIZE = 50;

  private final ChatMessageService chatMessageService;
  private WriteBehindBuffer<ChatMessage> writeBehindBuffer;

  @PostConstruct
  public void init() {
    this.writeBehindBuffer = new WriteBehindBuffer<>(
      "CommunityChat",
      FLUSH_BATCH_SIZE,
      chatMessageService::saveAll
    );
  }

  @Override
  public void enqueue(ChatMessage message) {
    if (message == null) {
      return;
    }
    if (message.getTimestamp() == null) {
      message.setTimestamp(System.currentTimeMillis());
    }
    writeBehindBuffer.enqueue(message);
  }

  @Scheduled(fixedRate = 1000)
  public void flushQueue() {
    if (writeBehindBuffer != null && !writeBehindBuffer.isEmpty()) {
      writeBehindBuffer.flushBatch();
    }
  }

  @Override
  public boolean deleteMessageByUuid(String messageUuid) {
    if (messageUuid == null || writeBehindBuffer == null) {
      return false;
    }

    boolean removedFromBuffer = writeBehindBuffer.removeIf(
      m -> Objects.equals(m.getMessageUuid(), messageUuid)
    );
    boolean removedFromDb = chatMessageService.deleteMessageByUuid(messageUuid);

    return removedFromBuffer || removedFromDb;
  }

  @Override
  public List<ChatMessage> getMessagesForRoom(ChatRoom room) {
    return chatMessageService.getMessagesForRoom(room);
  }

  @Override
  public List<ChatMessage> getMessagesForNewChatRoom(NewChatRoom newChatRoom) {
    List<ChatMessage> dbMessages = chatMessageService.getMessagesForNewChatRoom(newChatRoom);
    if (newChatRoom == null || newChatRoom.getRoomCode() == null || writeBehindBuffer == null) {
      return dbMessages;
    }

    String roomCode = newChatRoom.getRoomCode().toString();
    List<ChatMessage> pending = writeBehindBuffer.getPendingSnapshot().stream()
      .filter(m -> Objects.equals(m.getRoomCode(), roomCode))
      .collect(Collectors.toList());

    if (pending.isEmpty()) {
      return dbMessages;
    }

    List<ChatMessage> combined = new ArrayList<>(dbMessages.size() + pending.size());
    combined.addAll(dbMessages);
    combined.addAll(pending);
    combined.sort(Comparator.comparingLong(ChatMessage::getTimestamp));
    return combined;
  }

  @Override
  public boolean isPending(String messageUuid) {
    if (messageUuid == null || writeBehindBuffer == null) {
      return false;
    }
    return writeBehindBuffer.getPendingSnapshot().stream()
      .anyMatch(m -> Objects.equals(m.getMessageUuid(), messageUuid));
  }

  @PreDestroy
  public void shutdown() {
    if (writeBehindBuffer != null) {
      logger.info("Gracefully flushing pending Community Chat messages before shutdown...");
      writeBehindBuffer.shutdown();
    }
  }
}
