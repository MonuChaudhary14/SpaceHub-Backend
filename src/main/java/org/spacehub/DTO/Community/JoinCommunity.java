package org.spacehub.DTO.Community;

import lombok.Data;

import java.util.UUID;

@Data
public class JoinCommunity {

  private UUID communityId;
  private String userEmail;

}
