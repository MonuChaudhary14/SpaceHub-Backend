package org.spacehub.DTO.Community;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityBlockRequest {

  private UUID communityId;
  private String targetUserEmail;
  private String requesterEmail;
  private boolean block;

}
