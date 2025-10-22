package org.spacehub.DTO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoomRequestDTO {

  private String name;
  private String userId;
  private String roomCode;

}
