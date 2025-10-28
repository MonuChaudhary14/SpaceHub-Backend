package org.spacehub.repository.community;

import org.spacehub.entities.Community.CommunityUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommunityUserRepository extends JpaRepository<CommunityUser, Long> {

    List<CommunityUser> findByCommunityId(Long communityId);

}
