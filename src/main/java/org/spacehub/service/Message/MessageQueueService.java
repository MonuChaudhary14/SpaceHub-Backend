package org.spacehub.service.Message;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.handler.DirectChatWebSocketHandler;
import org.spacehub.kafka.event.DirectChatKafkaEvent;
import org.spacehub.kafka.producer.ChatKafkaProducer;
import org.spacehub.service.Interface.IMessageService;
import org.spacehub.utils.WriteBehindBuffer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageQueueService {

  private static final Logger logger = LoggerFactory.getLogger(MessageQueueService.class);
  private static final int FLUSH_BATCH_SIZE = 50;

  private final IMessageService messageService;
  private final ChatKafkaProducer chatKafkaProducer;
  private DirectChatWebSocketHandler messagingHandler;
  private WriteBehindBuffer<Message> writeBehindBuffer;

  @Autowired
  @Lazy
  public void setMessagingHandler(DirectChatWebSocketHandler handler) {
    this.messagingHandler = handler;
  }

  @PostConstruct
  public void init() {
    this.writeBehindBuffer = new WriteBehindBuffer<>(
      "DirectMessaging",
      FLUSH_BATCH_SIZE,
      this::persistAndBroadcastBatch
    );
  }

  private void persistAndBroadcastBatch(List<Message> batch) {
    try {
      List<Message> persisted = messageService.saveMessageBatch(batch);
      if (messagingHandler != null && persisted != null) {
        for (Message persistedMessage : persisted) {
          try {
            messagingHandler.confirmAndBroadcast(persistedMessage);
          }
          catch (Exception ignored) {
          }
        }
      }
    }
    catch (Exception e) {
      logger.error("Error persisting direct message batch: {}", e.getMessage(), e);
      throw e;
    }
  }

  public void enqueue(Message message) {
    if (message == null) {
      return;
    }
    if (message.getTimestamp() == null) {
      message.setTimestamp(System.currentTimeMillis());
    }
    if (message.getSenderEmail() != null) {
      message.setSenderEmail(message.getSenderEmail().trim().toLowerCase(Locale.ROOT));
    }
    if (message.getReceiverEmail() != null) {
      message.setReceiverEmail(message.getReceiverEmail().trim().toLowerCase(Locale.ROOT));
    }

    try {
      DirectChatKafkaEvent event = DirectChatKafkaEvent.builder()
        .messageUuid(message.getMessageUuid())
        .senderEmail(message.getSenderEmail())
        .receiverEmail(message.getReceiverEmail())
        .content(message.getContent())
        .fileKey(message.getFileKey())
        .fileName(message.getFileName())
        .contentType(message.getContentType())
        .timestamp(message.getTimestamp())
        .type(message.getType())
        .readStatus(message.getReadStatus())
        .build();

      chatKafkaProducer.sendDirectChatMessage(event).exceptionally(ex -> {
        logger.warn("Kafka direct message publish failed, falling back to local write-behind buffer: {}", ex.getMessage());
        writeBehindBuffer.enqueue(message);
        return null;
      });
    }
    catch (Exception e) {
      logger.warn("Kafka unavailable, routing direct message directly to local write-behind buffer: {}", e.getMessage());
      writeBehindBuffer.enqueue(message);
    }
  }

  @Scheduled(fixedRate = 1000)
  public void flushQueue() {
    if (writeBehindBuffer != null && !writeBehindBuffer.isEmpty()) {
      writeBehindBuffer.flushBatch();
    }
  }

  public boolean deleteMessageByUuid(String messageUuid) {
    if (messageUuid == null || writeBehindBuffer == null) {
      return false;
    }
    boolean removedFromMemory = writeBehindBuffer.removeIf(
      m -> Objects.equals(m.getMessageUuid(), messageUuid)
    );
    boolean removedFromDb = messageService.deleteMessageForUserByUuid(messageUuid, "") != null;
    return removedFromMemory || removedFromDb;
  }

  public List<Message> getPendingForChat(String userA, String userB) {
    if (writeBehindBuffer == null) {
      return List.of();
    }
    String chatKey = buildChatKey(userA, userB);
    return writeBehindBuffer.getPendingSnapshot().stream()
      .filter(m -> {
        String key = buildChatKey(m.getSenderEmail(), m.getReceiverEmail());
        return Objects.equals(key, chatKey);
      })
      .collect(Collectors.toList());
  }

  public boolean isPending(String messageUuid) {
    if (messageUuid == null || writeBehindBuffer == null) {
      return false;
    }
    return writeBehindBuffer.getPendingSnapshot().stream()
      .anyMatch(m -> Objects.equals(m.getMessageUuid(), messageUuid));
  }

  public String buildChatKey(String a, String b) {
    String aa = a == null ? "" : a.trim().toLowerCase(Locale.ROOT);
    String bb = b == null ? "" : b.trim().toLowerCase(Locale.ROOT);
    if (aa.compareTo(bb) <= 0) {
      return aa + "::" + bb;
    }
    return bb + "::" + aa;
  }

  @PreDestroy
  public void shutdown() {
    if (writeBehindBuffer != null) {
      logger.info("Gracefully flushing pending Direct Messages before shutdown...");
      writeBehindBuffer.shutdown();
    }
  }
}
