package org.spacehub.DTO.VoiceRoom;

import lombok.Data;
import org.spacehub.entities.VoiceRoom.VoiceRoom;

@Data
public class VoiceRoomDTO {

  private Long id;
  private String roomCode;
  private String name;
  private String createdBy;

  public VoiceRoomDTO(VoiceRoom entity) {
    this.id = entity.getId();
    this.roomCode = entity.getRoomCode();
    this.name = entity.getName();
    this.createdBy = entity.getCreatedBy();
  }
}
