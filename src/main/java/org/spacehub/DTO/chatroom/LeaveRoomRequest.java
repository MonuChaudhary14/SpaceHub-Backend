package org.spacehub.DTO.chatroom;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class LeaveRoomRequest {

  @NotBlank(message = "Room code is required")
  private UUID roomCode;

  @NotBlank(message = "Email is required")
  private String email;

}
