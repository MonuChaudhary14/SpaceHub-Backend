package org.spacehub.DTO.Community;

import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityInviteAcceptDTO {

  private String inviteCode;
  private Long communityId;
  private Long userId;

}
