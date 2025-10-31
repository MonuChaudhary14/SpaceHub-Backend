package org.spacehub.DTO.chatroom;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.spacehub.entities.Community.Role;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleChangeAction {

  private String roomCode;
  private String requesterId;
  private String targetUserId;
  private Role newRole;

}
