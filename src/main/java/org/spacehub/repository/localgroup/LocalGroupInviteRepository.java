package org.spacehub.repository.localgroup;

import org.spacehub.entities.LocalGroup.LocalGroupInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface LocalGroupInviteRepository extends JpaRepository<LocalGroupInvite, UUID> {

  Optional<LocalGroupInvite> findByInviteCode(String inviteCode);

  @Modifying
  @Transactional
  @Query("DELETE FROM LocalGroupInvite i WHERE i.localGroup.id = :groupId")
  void deleteByLocalGroupId(UUID groupId);

}
