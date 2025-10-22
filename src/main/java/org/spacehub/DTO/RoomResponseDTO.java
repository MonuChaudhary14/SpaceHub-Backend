package org.spacehub.DTO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoomResponseDTO {

  private String roomCode;
  private String name;
  private String message;

}
