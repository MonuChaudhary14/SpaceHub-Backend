package org.spacehub.repository.community;

import org.spacehub.entities.Community.CommunityUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.entities.User.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommunityUserRepository extends JpaRepository<CommunityUser, UUID> {

  List<CommunityUser> findByCommunityId(UUID communityId);
  List<CommunityUser> findByUserAndRole(User user, Role role);
  void deleteByUserId(UUID userId);
  Optional<CommunityUser> findByCommunityIdAndUserId(UUID communityId, UUID userId);

  @Query("SELECT COUNT(cu) FROM CommunityUser cu WHERE cu.community.communityId = :communityId")
  long countByCommunityId(@Param("communityId") UUID communityId);

  void deleteByCommunityId(UUID communityId);

}
