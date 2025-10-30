package org.spacehub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.spacehub.entities.LocalGroup.LocalGroup;

public interface LocalGroupRepository extends JpaRepository<LocalGroup, Long> {
}
