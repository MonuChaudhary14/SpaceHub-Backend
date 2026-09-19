package org.spacehub.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxKafkaEvent {
  private Long id;
  private String aggregateType;
  private String aggregateId;
  private String eventType;
  private String payload;
  private Long timestamp;
}
