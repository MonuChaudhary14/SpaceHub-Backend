package org.spacehub.repository.community;

import org.spacehub.entities.Community.CommunityInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityInviteRepository extends JpaRepository<CommunityInvite, UUID> {

  Optional<CommunityInvite> findByInviteCode(String inviteCode);
  List<CommunityInvite> findByCommunityId(UUID communityId);

}
