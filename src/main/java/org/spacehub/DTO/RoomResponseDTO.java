package org.spacehub.DTO;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
public class RoomResponseDTO implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;
  private String roomCode;
  private String name;
  private String message;
}

