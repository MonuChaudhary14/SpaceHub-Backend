package org.spacehub.DTO.chatroom;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.spacehub.entities.Community.Role;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangeRoleRequest {

  @NotBlank(message = "Room code is required")
  private String roomCode;

  @NotBlank(message = "Requester ID is required")
  private String requesterId;

  @NotBlank(message = "Target user ID is required")
  private String targetUserId;

  @NotNull(message = "New role is required")
  private Role newRole;

}
