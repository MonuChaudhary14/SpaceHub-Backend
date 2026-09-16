package org.spacehub.DTO.Community;

import lombok.Data;
import java.util.UUID;

@Data
public class LeaveCommunity {

  private UUID communityId;
  private String communityName;
}
