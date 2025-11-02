package org.spacehub.DTO.Community;

import lombok.Data;

import java.util.UUID;

@Data
public class CommunityMemberListRequest {
  private UUID communityId;
  private String requesterEmail;
}
