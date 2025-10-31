package org.spacehub.repository.ChatRoom;

import org.spacehub.entities.ChatRoom.ChatPoll;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatPollRepository extends JpaRepository<ChatPoll, Long> {

  List<ChatPoll> findByRoomOrderByTimestampAsc(ChatRoom room);

}
