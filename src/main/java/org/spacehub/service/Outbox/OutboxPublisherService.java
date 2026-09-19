package org.spacehub.service.Outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.entities.Outbox.OutboxMessage;
import org.spacehub.entities.Outbox.OutboxStatus;
import org.spacehub.kafka.event.OutboxKafkaEvent;
import org.spacehub.kafka.producer.ChatKafkaProducer;
import org.spacehub.repository.Outbox.OutboxRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OutboxPublisherService {

  private static final Logger logger = LoggerFactory.getLogger(OutboxPublisherService.class);
  private static final String RELAY_LOCK_KEY = "spacehub:lock:outbox-relay";

  private final OutboxRepository outboxRepository;
  private final ChatKafkaProducer chatKafkaProducer;
  private final ObjectMapper objectMapper;

  @Autowired(required = false)
  private RedissonClient redissonClient;

  @Transactional
  public OutboxMessage recordEvent(String aggregateType, String aggregateId, String eventType, Object payload) {
    return recordEvent(aggregateType, aggregateId, eventType, payload, UUID.randomUUID().toString(), aggregateId);
  }

  @Transactional
  public OutboxMessage recordEvent(
    String aggregateType,
    String aggregateId,
    String eventType,
    Object payload,
    String idempotencyKey,
    String partitionKey
  ) {
    try {
      String payloadJson = payload instanceof String ? (String) payload : objectMapper.writeValueAsString(payload);
      OutboxMessage outbox = OutboxMessage.builder()
        .aggregateType(aggregateType)
        .aggregateId(aggregateId)
        .eventType(eventType)
        .payload(payloadJson)
        .idempotencyKey(idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : UUID.randomUUID().toString())
        .partitionKey(partitionKey != null && !partitionKey.isBlank() ? partitionKey : aggregateId)
        .status(OutboxStatus.PENDING)
        .retryCount(0)
        .createdAt(Instant.now())
        .build();

      return outboxRepository.save(outbox);
    }
    catch (Exception e) {
      logger.error("Failed to record outbox event for aggregate {}: {}", aggregateId, e.getMessage(), e);
      throw new RuntimeException("Could not persist outbox event", e);
    }
  }

  @Scheduled(fixedDelay = 1000)
  public void relayPendingOutboxMessages() {
    if (redissonClient != null) {
      RLock lock = redissonClient.getLock(RELAY_LOCK_KEY);
      boolean acquired = false;
      try {
        acquired = lock.tryLock(0, 5, TimeUnit.SECONDS);
        if (acquired) {
          processPendingMessages();
        }
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.warn("Outbox relay lock acquisition interrupted: {}", e.getMessage());
      }
      finally {
        if (acquired && lock.isHeldByCurrentThread()) {
          lock.unlock();
        }
      }
    }
    else {
      processPendingMessages();
    }
  }

  @Transactional
  protected void processPendingMessages() {
    List<OutboxMessage> pendingMessages = outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
    if (pendingMessages.isEmpty()) {
      return;
    }

    logger.debug("Relaying {} pending outbox events to Kafka", pendingMessages.size());

    for (OutboxMessage outbox : pendingMessages) {
      OutboxKafkaEvent event = OutboxKafkaEvent.builder()
        .id(outbox.getId())
        .aggregateType(outbox.getAggregateType())
        .aggregateId(outbox.getAggregateId())
        .eventType(outbox.getEventType())
        .payload(outbox.getPayload())
        .idempotencyKey(outbox.getIdempotencyKey())
        .partitionKey(outbox.getPartitionKey())
        .headers(outbox.getHeaders())
        .timestamp(outbox.getCreatedAt().toEpochMilli())
        .build();

      try {
        chatKafkaProducer.sendOutboxEvent(event).whenComplete((result, ex) -> {
          if (ex != null) {
            logger.error("Outbox relay failed for event ID {}: {}", outbox.getId(), ex.getMessage());
            outboxRepository.updateStatusWithError(outbox.getId(), OutboxStatus.FAILED, ex.getMessage(), Instant.now());
          }
          else {
            logger.debug("Outbox event ID {} successfully relayed to Kafka", outbox.getId());
            outboxRepository.updateStatus(outbox.getId(), OutboxStatus.PUBLISHED, Instant.now());
          }
        });
      }
      catch (Exception e) {
        logger.error("Exception triggering outbox send for ID {}: {}", outbox.getId(), e.getMessage());
        outboxRepository.updateStatusWithError(outbox.getId(), OutboxStatus.FAILED, e.getMessage(), Instant.now());
      }
    }
  }
}
