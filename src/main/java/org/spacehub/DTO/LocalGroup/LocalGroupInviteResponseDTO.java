package org.spacehub.DTO.LocalGroup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

