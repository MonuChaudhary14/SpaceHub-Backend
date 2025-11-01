package org.spacehub.DTO;

import lombok.Data;

import java.util.UUID;

@Data
public class CancelJoinRequest {

  private UUID communityId;
  private String userEmail;

}
