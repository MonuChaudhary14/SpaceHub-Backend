package org.spacehub.DTO.Community;

import lombok.Data;

@Data
public class CancelJoinRequest {

  private String communityName;
  private String userEmail;

}
