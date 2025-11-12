package org.spacehub.repository.community;

import io.lettuce.core.dynamic.annotation.Param;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.User.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommunityRepository extends JpaRepository<Community, UUID> {

  Community findByName(String name);
  @NonNull
  List<Community> findAll();
  boolean existsByNameIgnoreCase(String name);

  @Query("SELECT c FROM Community c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :pattern, '%'))")
  Page<Community> searchByNamePattern(@Param("pattern") String pattern, Pageable pageable);

  @Query("SELECT c FROM Community c LEFT JOIN FETCH c.communityUsers WHERE c.createdBy = :user")
  List<Community> findAllByCreatedByWithUsers(@Param("user") User user);

  @Query("SELECT c FROM Community c JOIN FETCH c.communityUsers cu WHERE cu.user = :user AND c.createdBy != :user")
  List<Community> findAllWhereUserIsMember(@Param("user") User user);

  @Query("SELECT c FROM Community c JOIN c.pendingRequests p WHERE p = :user")
  List<Community> findAllWithPendingUser(@Param("user") User user);

}
