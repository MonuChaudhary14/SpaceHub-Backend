package org.spacehub.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.kafka.KafkaTopicConfig;
import org.spacehub.kafka.event.CommunityChatKafkaEvent;
import org.spacehub.kafka.event.DirectChatKafkaEvent;
import org.spacehub.kafka.event.OutboxKafkaEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ChatKafkaProducer {

  private static final Logger logger = LoggerFactory.getLogger(ChatKafkaProducer.class);

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  public CompletableFuture<SendResult<String, String>> sendCommunityChatMessage(CommunityChatKafkaEvent event) {
    String key = event.getRoomCode() != null ? event.getRoomCode() : "default_room";
    try {
      String payload = objectMapper.writeValueAsString(event);
      return kafkaTemplate.send(KafkaTopicConfig.TOPIC_COMMUNITY_CHAT, key, payload)
        .whenComplete((result, ex) -> {
          if (ex != null) {
            logger.error("Failed to send community chat message [UUID: {}] to Kafka: {}",
              event.getMessageUuid(), ex.getMessage());
          }
          else {
            logger.debug("Successfully published community message [UUID: {}] to partition {}",
              event.getMessageUuid(), result.getRecordMetadata().partition());
          }
        });
    }
    catch (Exception e) {
      logger.error("Serialization error publishing community chat message [UUID: {}]: {}",
        event.getMessageUuid(), e.getMessage(), e);
      CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
      failed.completeExceptionally(e);
      return failed;
    }
  }

  public CompletableFuture<SendResult<String, String>> sendDirectChatMessage(DirectChatKafkaEvent event) {
    String key = computeDirectChatKey(event.getSenderEmail(), event.getReceiverEmail());
    try {
      String payload = objectMapper.writeValueAsString(event);
      return kafkaTemplate.send(KafkaTopicConfig.TOPIC_DIRECT_CHAT, key, payload)
        .whenComplete((result, ex) -> {
          if (ex != null) {
            logger.error("Failed to send direct chat message [UUID: {}] to Kafka: {}",
              event.getMessageUuid(), ex.getMessage());
          }
          else {
            logger.debug("Successfully published direct message [UUID: {}] to partition {}",
              event.getMessageUuid(), result.getRecordMetadata().partition());
          }
        });
    }
    catch (Exception e) {
      logger.error("Serialization error publishing direct chat message [UUID: {}]: {}",
        event.getMessageUuid(), e.getMessage(), e);
      CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
      failed.completeExceptionally(e);
      return failed;
    }
  }

  public CompletableFuture<SendResult<String, String>> sendOutboxEvent(OutboxKafkaEvent event) {
    String key = event.getAggregateId() != null ? event.getAggregateId() : String.valueOf(event.getId());
    try {
      String payload = objectMapper.writeValueAsString(event);
      return kafkaTemplate.send(KafkaTopicConfig.TOPIC_OUTBOX_EVENTS, key, payload)
        .whenComplete((result, ex) -> {
          if (ex != null) {
            logger.error("Failed to send outbox event [ID: {}] to Kafka: {}",
              event.getId(), ex.getMessage());
          }
          else {
            logger.debug("Successfully published outbox event [ID: {}] to partition {}",
              event.getId(), result.getRecordMetadata().partition());
          }
        });
    }
    catch (Exception e) {
      logger.error("Serialization error publishing outbox event [ID: {}]: {}",
        event.getId(), e.getMessage(), e);
      CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
      failed.completeExceptionally(e);
      return failed;
    }
  }

  public static String computeDirectChatKey(String email1, String email2) {
    if (email1 == null) {
      email1 = "";
    }
    if (email2 == null) {
      email2 = "";
    }
    email1 = email1.trim().toLowerCase(Locale.ROOT);
    email2 = email2.trim().toLowerCase(Locale.ROOT);
    return email1.compareTo(email2) < 0 ? email1 + ":" + email2 : email2 + ":" + email1;
  }
}
