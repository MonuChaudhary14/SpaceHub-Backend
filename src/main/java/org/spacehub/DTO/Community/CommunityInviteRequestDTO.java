package org.spacehub.DTO.Community;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityInviteRequestDTO {

  private int maxUses = 10;
  private int expiresInHours = 72;

}
