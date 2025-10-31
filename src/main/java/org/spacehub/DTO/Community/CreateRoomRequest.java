package org.spacehub.DTO.Community;

import lombok.Data;

@Data
public class CreateRoomRequest {
  private Long communityId;
  private String roomName;
  private String requesterEmail;
}
