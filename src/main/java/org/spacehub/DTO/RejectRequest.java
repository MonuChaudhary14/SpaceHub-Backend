package org.spacehub.DTO;

import lombok.Data;

@Data
public class RejectRequest {

  private String communityName;
  private String creatorEmail;
  private String userEmail;

}
