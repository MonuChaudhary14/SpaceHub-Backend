package org.spacehub.repository;

import org.spacehub.entities.Group.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {
    List<Group> findByCommunityId(UUID communityId);
}
