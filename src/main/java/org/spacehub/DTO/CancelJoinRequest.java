package org.spacehub.DTO;

import lombok.Data;

@Data
public class CancelJoinRequest {

  private String communityName;
  private String userEmail;

}
