package org.spacehub.repository.community;

import org.spacehub.entities.Community.Community;
import org.spacehub.entities.User.User;
import org.spacehub.entities.Community.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Repository
public interface CommunityRepository extends JpaRepository<Community, Long> {

  Community findByName(String name);
  @NonNull
  List<Community> findAll();
  boolean existsByNameIgnoreCase(String name);

  Page<Community> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String name, String description, Pageable pageable);

  List<Community> findByMembersContaining(User user);

  List<Community> findDistinctByCommunityUsers_UserAndCommunityUsers_RoleIn(User user, List<Role> roles);
}
