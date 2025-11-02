package org.spacehub.DTO.Community;

import lombok.Data;

import java.util.UUID;

@Data
public class CommunityMemberRequest {
  private UUID communityId;
  private String userEmail;
  private String requesterEmail;
}
