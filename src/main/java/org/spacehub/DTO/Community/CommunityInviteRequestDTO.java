package org.spacehub.DTO.Community;


import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityInviteRequestDTO {

  private Long inviterId;
  private String email;
  private int maxUses;
  private int expiresInHours = 72;

}
