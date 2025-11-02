package org.spacehub.repository.localgroup;

import org.spacehub.entities.LocalGroup.LocalGroupInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LocalGroupInviteRepository extends JpaRepository<LocalGroupInvite, UUID> {

    Optional<LocalGroupInvite> findByInviteCode(String inviteCode);

}
