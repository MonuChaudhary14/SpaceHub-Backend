package org.spacehub.DTO.Community;

import lombok.Data;

import java.util.UUID;

@Data
public class DeleteCommunityDTO {

  private UUID communityId;
  private String userEmail;

}
