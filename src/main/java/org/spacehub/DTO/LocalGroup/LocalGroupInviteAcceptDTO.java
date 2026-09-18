package org.spacehub.DTO.LocalGroup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalGroupInviteAcceptDTO {

  private UUID groupId;
  private String inviteCode;

}
