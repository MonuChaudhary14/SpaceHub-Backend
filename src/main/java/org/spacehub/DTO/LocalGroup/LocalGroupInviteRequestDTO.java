package org.spacehub.DTO.LocalGroup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalGroupInviteRequestDTO {

  private int maxUses;
  private int expiresInHours;

}
