package org.spacehub.DTO.Community;

import lombok.Data;
import java.util.UUID;

@Data
public class CancelJoinRequest {

  private UUID communityId;
  private String communityName;
}
