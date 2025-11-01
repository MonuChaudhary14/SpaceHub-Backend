package org.spacehub.DTO.chatroom;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.spacehub.entities.Community.Role;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleChangeAction {

  @NotNull
  private UUID roomCode;

  @NotNull
  private UUID requesterId;

  @NotNull
  private UUID targetUserId;

  @NotNull
  private Role newRole;

}
