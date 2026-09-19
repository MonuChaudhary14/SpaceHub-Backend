package org.spacehub.entities.Outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
  name = "processed_events",
  indexes = {
    @Index(name = "idx_processed_event_key", columnList = "idempotencyKey", unique = true),
    @Index(name = "idx_processed_event_created", columnList = "processedAt")
  }
)
public class ProcessedEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 100)
  private String idempotencyKey;

  @Column(nullable = false, length = 100)
  private String eventType;

  @Column(nullable = false, length = 100)
  private String consumerGroup;

  @Column(nullable = false)
  private Instant processedAt;

  @PrePersist
  public void prePersist() {
    if (this.processedAt == null) {
      this.processedAt = Instant.now();
    }
  }
}
