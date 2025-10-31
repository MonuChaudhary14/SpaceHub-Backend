package org.spacehub.DTO.Community;

import lombok.Data;

@Data
public class CommunityMemberListRequest {
  private Long communityId;
  private String requesterEmail;
}
