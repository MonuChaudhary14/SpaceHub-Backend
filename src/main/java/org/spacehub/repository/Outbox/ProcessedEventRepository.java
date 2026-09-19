package org.spacehub.repository.Outbox;

import org.spacehub.entities.Outbox.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

  boolean existsByIdempotencyKey(String idempotencyKey);

  Optional<ProcessedEvent> findByIdempotencyKey(String idempotencyKey);
}
