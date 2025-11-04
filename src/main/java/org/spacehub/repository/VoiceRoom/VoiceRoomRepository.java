package org.spacehub.repository.VoiceRoom;

import org.spacehub.entities.VoiceRoom.VoiceRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface VoiceRoomRepository extends JpaRepository<VoiceRoom, UUID> {



}
