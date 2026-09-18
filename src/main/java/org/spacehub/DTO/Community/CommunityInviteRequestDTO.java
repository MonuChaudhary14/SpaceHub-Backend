package org.spacehub.DTO.Community;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityInviteRequestDTO {

  @Builder.Default
  private int maxUses = 10;
  @Builder.Default
  private int expiresInHours = 72;

}
