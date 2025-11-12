package org.spacehub.repository.ChatRoom;

import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.NewChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long>{

  List<ChatMessage> findByRoomOrderByTimestampAsc(ChatRoom room);

  List<ChatMessage> findByNewChatRoomOrderByTimestampAsc(NewChatRoom newChatRoom);

  Optional<ChatMessage> findByMessageUuid(String messageUuid);

  void deleteByMessageUuid(String messageUuid);

}
