package org.spacehub.DTO.Community;

import lombok.Data;

@Data
public class RejectRequest {

  private String communityName;
  private String userEmail;

}
