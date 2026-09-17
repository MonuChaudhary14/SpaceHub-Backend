package org.spacehub.repository.friend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.spacehub.entities.Friends.Friends;
import org.spacehub.entities.User.User;

import java.util.List;
import java.util.Optional;

import java.util.UUID;

@Repository
public interface FriendsRepository extends JpaRepository<Friends, UUID> {

  List<Friends> findByUserAndStatus(User user, String status);

  List<Friends> findByFriendAndStatus(User friend, String status);

  List<Friends> findByUserAndStatusIgnoreCase(User user, String status);

  List<Friends> findByFriendAndStatusIgnoreCase(User friend, String status);

  Optional<Friends> findByUserAndFriend(User user, User friend);

  Optional<Friends> findByUserAndFriendAndStatus(User user, User friend, String status);

  Optional<Friends> findByUserAndFriendAndStatusIgnoreCase(User user, User friend, String status);

}
