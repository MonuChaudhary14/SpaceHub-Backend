package org.spacehub.DTO.LocalGroup;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalGroupInviteAcceptDTO {

  private UUID groupId;
  private String inviteCode;
  private String acceptorEmail;

}
