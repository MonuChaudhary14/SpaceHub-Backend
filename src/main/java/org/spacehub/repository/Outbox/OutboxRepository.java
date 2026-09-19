package org.spacehub.repository.Outbox;

import org.spacehub.entities.Outbox.OutboxMessage;
import org.spacehub.entities.Outbox.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

  List<OutboxMessage> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);

  @Modifying
  @Query("UPDATE OutboxMessage o SET o.status = :status, o.processedAt = :processedAt WHERE o.id = :id")
  void updateStatus(
    @Param("id") Long id,
    @Param("status") OutboxStatus status,
    @Param("processedAt") Instant processedAt
  );

  @Modifying
  @Query("UPDATE OutboxMessage o SET o.status = :status, o.errorMessage = :errorMessage, " +
    "o.processedAt = :processedAt WHERE o.id = :id")
  void updateStatusWithError(
    @Param("id") Long id,
    @Param("status") OutboxStatus status,
    @Param("errorMessage") String errorMessage,
    @Param("processedAt") Instant processedAt
  );
}
