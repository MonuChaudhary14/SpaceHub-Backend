package org.spacehub.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.spacehub.entities.LocalGroup.LocalGroup;

public interface LocalGroupRepository extends JpaRepository<LocalGroup, Long> {
  Page<LocalGroup> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
    String name, String description, Pageable pageable);
}
