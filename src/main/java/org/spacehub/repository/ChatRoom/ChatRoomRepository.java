package org.spacehub.repository.ChatRoom;

import org.spacehub.entities.ChatRoom.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID>{

  Optional<ChatRoom> findByRoomCode(UUID roomCode);

  List<ChatRoom> findByCommunityId(UUID communityId);

}
