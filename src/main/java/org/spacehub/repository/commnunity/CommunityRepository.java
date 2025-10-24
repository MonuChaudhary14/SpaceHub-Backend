package org.spacehub.repository.commnunity;

import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.User.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityRepository extends JpaRepository<Community, Long> {

    List<Community> findByCreatedBy(User user);

    Community findByName(String name);

    Optional<Community> findByNameAndCreatedById(String name, Long createdById);

}
