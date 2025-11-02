package org.spacehub.DTO.Community;

import lombok.Data;

import java.util.UUID;

@Data
public class CommunityChangeRoleRequest {

  private UUID communityId;
  private String targetUserEmail;
  private String requesterEmail;
  private String newRole;

}
