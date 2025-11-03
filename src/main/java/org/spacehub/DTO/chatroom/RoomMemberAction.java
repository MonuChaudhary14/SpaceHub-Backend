package org.spacehub.DTO.chatroom;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomMemberAction {

  @NotNull
  private UUID roomCode;

  @NotNull
  private String Email;

  @NotNull
  private String targetEmail;

}
