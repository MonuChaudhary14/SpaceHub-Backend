package org.spacehub.repository.ChatRoom;

import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.ChatRoomUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomUserRepository extends JpaRepository<ChatRoomUser, Long> {

  List<ChatRoomUser> findByRoom(ChatRoom room);

  Optional<ChatRoomUser> findByRoomAndEmail(ChatRoom room, String email);

  void deleteByRoomAndEmail(ChatRoom room, String email);

}
