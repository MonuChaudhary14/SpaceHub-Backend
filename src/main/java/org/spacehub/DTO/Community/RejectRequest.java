package org.spacehub.DTO.Community;

import lombok.Data;
import java.util.UUID;

@Data
public class RejectRequest {

  private UUID communityId;
  private String communityName;
  private String userEmail;

}
