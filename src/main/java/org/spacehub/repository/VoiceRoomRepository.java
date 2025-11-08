package org.spacehub.repository;

import org.spacehub.entities.VoiceRoom.VoiceRoom;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VoiceRoomRepository extends JpaRepository<VoiceRoom, Long> {
  Optional<VoiceRoom> findByNameAndChatRoom(String name, ChatRoom chatRoom);
  List<VoiceRoom> findByChatRoom(ChatRoom chatRoom);
}
