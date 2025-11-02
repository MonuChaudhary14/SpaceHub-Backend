package org.spacehub.DTO.Community;

import lombok.Data;

import java.util.UUID;

@Data
public class CommunityRoomsRequest {
  private UUID communityId;
}
