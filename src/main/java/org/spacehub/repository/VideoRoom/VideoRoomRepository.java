package org.spacehub.repository.videoRoom;

import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.VideoRoom.VideoRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VideoRoomRepository extends JpaRepository<VideoRoom, Long> {

    Optional<VideoRoom> findByNameAndChatRoom(String name, ChatRoom chatRoom);

    List<VideoRoom> findByChatRoom(ChatRoom chatRoom);

}
