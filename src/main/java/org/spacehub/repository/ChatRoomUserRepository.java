package org.spacehub.repository;

import org.spacehub.entities.ChatRoom;
import org.spacehub.entities.ChatRoomUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomUserRepository extends JpaRepository<ChatRoomUser, Long> {

    List<ChatRoomUser> findByRoom(ChatRoom room);

    Optional<ChatRoomUser> findByRoomAndUserId(ChatRoom room, String userId);

    void deleteByRoom(ChatRoom room);

    void deleteByRoomAndUserId(ChatRoom room, String userId);
}
