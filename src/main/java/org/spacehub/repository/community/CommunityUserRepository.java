package org.spacehub.repository.community;

import org.spacehub.entities.Community.CommunityUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.entities.User.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommunityUserRepository extends JpaRepository<CommunityUser, Long> {

  List<CommunityUser> findByCommunityId(Long communityId);
  List<CommunityUser> findByUserAndRole(User user, Role role);

}
