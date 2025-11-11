package org.spacehub.DTO.Community;

import lombok.Data;

@Data
public class AcceptRequest {

  private String communityName;
  private String creatorEmail;
  private String userEmail;

}
