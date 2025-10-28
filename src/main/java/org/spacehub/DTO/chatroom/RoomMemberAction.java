package org.spacehub.DTO.chatroom;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomMemberAction {

  private String roomCode;
  private String userId;
  private String targetUserId;

}
