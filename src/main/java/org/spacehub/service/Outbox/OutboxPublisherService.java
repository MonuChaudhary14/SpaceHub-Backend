package org.spacehub.service.Outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.entities.Outbox.OutboxMessage;
import org.spacehub.entities.Outbox.OutboxStatus;
import org.spacehub.kafka.event.OutboxKafkaEvent;
import org.spacehub.kafka.producer.ChatKafkaProducer;
import org.spacehub.repository.Outbox.OutboxRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxPublisherService {

  private static final Logger logger = LoggerFactory.getLogger(OutboxPublisherService.class);

  private final OutboxRepository outboxRepository;
  private final ChatKafkaProducer chatKafkaProducer;
  private final ObjectMapper objectMapper;

  @Transactional
  public OutboxMessage recordEvent(String aggregateType, String aggregateId, String eventType, Object payload) {
    try {
      String payloadJson = payload instanceof String ? (String) payload : objectMapper.writeValueAsString(payload);
      OutboxMessage outbox = OutboxMessage.builder()
        .aggregateType(aggregateType)
        .aggregateId(aggregateId)
        .eventType(eventType)
        .payload(payloadJson)
        .status(OutboxStatus.PENDING)
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
  @Transactional
  public void relayPendingOutboxMessages() {
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
