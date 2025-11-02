package org.spacehub.DTO.Community;

import lombok.Data;

import java.util.UUID;

@Data
public class CreateRoomRequest {
  private UUID communityId;
  private String roomName;
  private String requesterEmail;
}
