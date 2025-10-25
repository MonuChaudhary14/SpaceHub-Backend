package org.spacehub.repository;

import org.spacehub.entities.Friends.FriendStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.spacehub.entities.Friends.Friends;
import org.spacehub.entities.User.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendsRepository extends JpaRepository<Friends, Long> {

    Optional<Friends> findByUserAndFriend(User user, User friend);

    Optional<Friends> findByUserAndFriendAndStatus(User user, User friend, FriendStatus status);

    List<Friends> findByUserAndStatus(User user, FriendStatus status);

    List<Friends> findByFriendAndStatus(User user, FriendStatus status);

    default Optional<Friends> findByUsers(User user1, User user2) {
        Optional<Friends> friend = findByUserAndFriend(user1, user2);
        return friend.isPresent() ? friend : findByUserAndFriend(user2, user1);
    }
}
