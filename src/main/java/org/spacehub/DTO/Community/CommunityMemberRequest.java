package org.spacehub.DTO.Community;

import lombok.Data;

@Data
public class CommunityMemberRequest {
  private Long communityId;
  private String userEmail;
  private String requesterEmail;
}
