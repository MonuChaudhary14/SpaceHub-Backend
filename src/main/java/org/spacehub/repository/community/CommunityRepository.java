package org.spacehub.repository.community;

import org.spacehub.entities.Community.Community;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommunityRepository extends JpaRepository<Community, Long> {

  Community findByName(String name);

}
