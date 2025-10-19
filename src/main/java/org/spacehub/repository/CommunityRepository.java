package org.spacehub.repository;

import org.spacehub.entities.Community;
import org.spacehub.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommunityRepository extends JpaRepository<Community, Integer> {

    List<Community> findByCreatedBy(User user);

    Community findByName(String name);

}
