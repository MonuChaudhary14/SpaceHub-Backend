package org.spacehub.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.entities.Outbox.ProcessedEvent;
import org.spacehub.kafka.KafkaTopicConfig;
import org.spacehub.kafka.event.OutboxKafkaEvent;
import org.spacehub.repository.Outbox.ProcessedEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class OutboxKafkaConsumer {

  private static final Logger logger = LoggerFactory.getLogger(OutboxKafkaConsumer.class);
  private static final String CONSUMER_GROUP = "spacehub-outbox-event-consumer-group";
  private static final String REDIS_IDEMPOTENCY_PREFIX = "spacehub:idempotency:";
  private static final Duration IDEMPOTENCY_CACHE_TTL = Duration.ofHours(24);

  private final ProcessedEventRepository processedEventRepository;
  private final ObjectMapper objectMapper;

  @Autowired(required = false)
  private StringRedisTemplate redisTemplate;

  @KafkaListener(
    topics = KafkaTopicConfig.TOPIC_OUTBOX_EVENTS,
    groupId = CONSUMER_GROUP,
    containerFactory = "kafkaListenerContainerFactory"
  )
  @Transactional
  public void consumeOutboxEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
    if (record == null || record.value() == null || record.value().isBlank()) {
      ack.acknowledge();
      return;
    }

    try {
      OutboxKafkaEvent event = objectMapper.readValue(record.value(), OutboxKafkaEvent.class);
      String idempotencyKey = event.getIdempotencyKey() != null && !event.getIdempotencyKey().isBlank()
        ? event.getIdempotencyKey()
        : "legacy-event-" + event.getId();

      if (isDuplicateEvent(idempotencyKey)) {
        logger.debug("Skipping duplicate outbox event [IdempotencyKey: {}, EventType: {}]",
          idempotencyKey, event.getEventType());
        ack.acknowledge();
        return;
      }

      processEvent(event);
      markEventProcessed(idempotencyKey, event.getEventType());

      ack.acknowledge();
      logger.debug("Successfully processed outbox event [Id: {}, AggregateId: {}, EventType: {}]",
        event.getId(), event.getAggregateId(), event.getEventType());
    }
    catch (Exception e) {
      logger.error("Failed to process outbox event record [Offset: {}, Partition: {}]: {}",
        record.offset(), record.partition(), e.getMessage(), e);
      throw new RuntimeException("Error processing outbox Kafka event", e);
    }
  }

  private boolean isDuplicateEvent(String idempotencyKey) {
    if (redisTemplate != null) {
      Boolean existsInCache = redisTemplate.hasKey(REDIS_IDEMPOTENCY_PREFIX + idempotencyKey);
      if (Boolean.TRUE.equals(existsInCache)) {
        return true;
      }
    }
    return processedEventRepository.existsByIdempotencyKey(idempotencyKey);
  }

  private void markEventProcessed(String idempotencyKey, String eventType) {
    ProcessedEvent processedEvent = ProcessedEvent.builder()
      .idempotencyKey(idempotencyKey)
      .eventType(eventType != null ? eventType : "UNKNOWN")
      .consumerGroup(CONSUMER_GROUP)
      .processedAt(Instant.now())
      .build();

    processedEventRepository.save(processedEvent);

    if (redisTemplate != null) {
      try {
        redisTemplate.opsForValue().set(
          REDIS_IDEMPOTENCY_PREFIX + idempotencyKey,
          Instant.now().toString(),
          IDEMPOTENCY_CACHE_TTL
        );
      }
      catch (Exception e) {
        logger.warn("Failed to set Redis idempotency key {}: {}", idempotencyKey, e.getMessage());
      }
    }
  }

  private void processEvent(OutboxKafkaEvent event) {
    logger.info("Dispatching Outbox Event: [Type: {}, Aggregate: {}:{}, Key: {}]",
      event.getEventType(), event.getAggregateType(), event.getAggregateId(), event.getPartitionKey());
  }
}
