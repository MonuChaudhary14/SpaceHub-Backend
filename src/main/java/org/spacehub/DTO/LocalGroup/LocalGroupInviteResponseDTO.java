package org.spacehub.DTO.LocalGroup;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalGroupInviteResponseDTO {

  private UUID groupId;
  private String inviteCode;
  private String inviteLink;
  private String inviterEmail;
  private int maxUses;
  private int uses;
  private LocalDateTime expiresAt;
  private String status;

}

