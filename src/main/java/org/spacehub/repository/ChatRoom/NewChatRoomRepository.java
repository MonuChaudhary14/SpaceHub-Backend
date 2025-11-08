package org.spacehub.repository.ChatRoom;

import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.NewChatRoom;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NewChatRoomRepository extends JpaRepository<NewChatRoom, UUID> {

  List<NewChatRoom> findByChatRoom(ChatRoom chatRoom);

  Optional<NewChatRoom> findByRoomCode(UUID roomCode);

}
