package org.spacehub.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.spacehub.entities.LocalGroup.LocalGroup;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LocalGroupRepository extends JpaRepository<LocalGroup, Long> {
  Page<LocalGroup> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
    String name, String description, Pageable pageable);
  @Query("SELECT lg FROM LocalGroup lg LEFT JOIN FETCH lg.createdBy LEFT JOIN FETCH lg.members")
  List<LocalGroup> findAllWithCreatorAndMembers();
}
