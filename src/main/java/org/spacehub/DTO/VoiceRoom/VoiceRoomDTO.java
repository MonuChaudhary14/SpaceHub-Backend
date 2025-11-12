package org.spacehub.DTO.VoiceRoom;

import lombok.Data;
import org.spacehub.entities.VoiceRoom.VoiceRoom;

@Data
public class VoiceRoomDTO {

  private int janusRoomId;
  private String name;
  private String createdBy;

  public VoiceRoomDTO(VoiceRoom entity) {
    this.janusRoomId = entity.getJanusRoomId();
    this.name = entity.getName();
    this.createdBy = entity.getCreatedBy();
  }
}
