package org.spacehub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.spacehub.entities.Friends;
import org.spacehub.entities.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendsRepository extends JpaRepository<Friends, Long> {

    List<Friends> findByUserAndStatus(User user, String status);

    List<Friends> findByFriendAndStatus(User friend, String status);

    Optional<Friends> findByUserAndFriend(User user, User friend);

    Optional<Friends> findByUserAndFriendAndStatus(User user, User friend, String status);
}
