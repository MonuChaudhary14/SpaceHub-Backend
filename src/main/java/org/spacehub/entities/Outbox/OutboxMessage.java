package org.spacehub.entities.Outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
  name = "outbox_messages",
  indexes = {
    @Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
    @Index(name = "idx_outbox_aggregate", columnList = "aggregate_type, aggregate_id"),
    @Index(name = "idx_outbox_idempotency", columnList = "idempotency_key", unique = true)
  }
)
public class OutboxMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String aggregateType;

  @Column(nullable = false, length = 100)
  private String aggregateId;

  @Column(nullable = false, length = 100)
  private String eventType;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  @Column(length = 100, unique = true, nullable = false)
  private String idempotencyKey;

  @Column(length = 100)
  private String partitionKey;

  @Column(columnDefinition = "TEXT")
  private String headers;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private OutboxStatus status = OutboxStatus.PENDING;

  @Column(nullable = false)
  @Builder.Default
  private Integer retryCount = 0;

  @Column(nullable = false)
  private Instant createdAt;

  private Instant processedAt;

  @Column(length = 1000)
  private String errorMessage;

  @PrePersist
  public void prePersist() {
    if (this.createdAt == null) {
      this.createdAt = Instant.now();
    }
    if (this.status == null) {
      this.status = OutboxStatus.PENDING;
    }
    if (this.retryCount == null) {
      this.retryCount = 0;
    }
    if (this.idempotencyKey == null || this.idempotencyKey.isBlank()) {
      this.idempotencyKey = UUID.randomUUID().toString();
    }
    if (this.partitionKey == null || this.partitionKey.isBlank()) {
      this.partitionKey = this.aggregateId;
    }
  }
}
