package org.spacehub.repository.localgroup;

import io.lettuce.core.dynamic.annotation.Param;
import org.spacehub.entities.User.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.spacehub.entities.LocalGroup.LocalGroup;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocalGroupRepository extends JpaRepository<LocalGroup, UUID> {
  Page<LocalGroup> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
    String name, String description, Pageable pageable);
  @Query("SELECT lg FROM LocalGroup lg LEFT JOIN FETCH lg.createdBy LEFT JOIN FETCH lg.members")
  List<LocalGroup> findAllWithCreatorAndMembers();
  List<LocalGroup> findAllByCreatedBy(User user);

  @Query("SELECT g FROM LocalGroup g JOIN g.members m WHERE m = :user AND g.createdBy != :user")
  List<LocalGroup> findAllWhereUserIsMember(@Param("user") User user);

  Optional<LocalGroup> findByNameIgnoreCaseAndCreatedBy(String name, User createdBy);

  boolean existsByNameIgnoreCase(String name);
}
