package org.spacehub.repository.community;

import org.spacehub.entities.Community.CommunityInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommunityInviteRepository extends JpaRepository<CommunityInvite, Long> {
  Optional<CommunityInvite> findByInviteCode(String inviteCode);
}
