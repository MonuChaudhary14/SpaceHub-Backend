package org.spacehub.repository;

import org.spacehub.entities.ChatMessage;
import org.spacehub.entities.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long>{

    List<ChatMessage> findByRoomOrderByTimestampAsc(ChatRoom room);

}
