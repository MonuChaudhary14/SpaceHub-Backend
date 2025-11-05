package org.spacehub.repository.community;

import org.spacehub.entities.Community.CommunityUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.entities.User.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommunityUserRepository extends JpaRepository<CommunityUser, UUID> {

  List<CommunityUser> findByCommunityId(UUID communityId);
  List<CommunityUser> findByUserAndRole(User user, Role role);
  void deleteByUserId(UUID userId);
  Optional<CommunityUser> findByCommunityIdAndUserId(UUID communityId, UUID userId);

}
