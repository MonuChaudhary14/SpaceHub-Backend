package org.spacehub.DTO.LocalGroup;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalGroupInviteRequestDTO {

  private String inviterEmail;
  private int maxUses;
  private int expiresInHours;

}
