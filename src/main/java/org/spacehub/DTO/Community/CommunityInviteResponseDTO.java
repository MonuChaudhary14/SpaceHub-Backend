package org.spacehub.DTO.Community;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityInviteResponseDTO {
  private String inviteCode;
  private String inviteLink;
  private UUID communityId;
  private String email;
  private int maxUses;
  private int uses;
  private LocalDateTime expiresAt;
  private String status;
}
