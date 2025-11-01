package org.spacehub.repository.community;

import org.spacehub.entities.Community.Community;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommunityRepository extends JpaRepository<Community, Long> {

  Community findByName(String name);

  Optional<Community> findByCommunityId(UUID communityId);

  @NonNull
  List<Community> findAll();
  boolean existsByNameIgnoreCase(String name);
  Page<Community> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String name, String description,
                                                                                  Pageable pageable);
}
