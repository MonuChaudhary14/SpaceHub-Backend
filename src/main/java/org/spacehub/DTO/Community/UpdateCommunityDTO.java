package org.spacehub.DTO.Community;

import lombok.Data;

import java.util.UUID;

@Data
public class UpdateCommunityDTO {

  private UUID communityId;
  private String name;
  private String description;
  private String requesterEmail;
}
