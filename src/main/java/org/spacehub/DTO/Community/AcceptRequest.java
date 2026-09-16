package org.spacehub.DTO.Community;

import lombok.Data;
import java.util.UUID;

@Data
public class AcceptRequest {

  private UUID communityId;
  private String communityName;
  private String userEmail;

}
