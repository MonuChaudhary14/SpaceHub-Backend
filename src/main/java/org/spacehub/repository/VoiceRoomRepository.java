package org.spacehub.repository;

import org.spacehub.entities.VoiceRoom.VoiceRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VoiceRoomRepository extends JpaRepository<VoiceRoom, Long> {

    Optional<VoiceRoom> findByRoomCode(String roomCode);

}
