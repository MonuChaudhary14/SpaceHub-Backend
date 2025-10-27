package org.spacehub.repository.commnunity;

import org.spacehub.entities.Community.Community;
import org.spacehub.entities.User.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommunityRepository extends JpaRepository<Community, Long> {

    List<Community> findByCreatedBy(User user);

    Community findByName(String name);

}
