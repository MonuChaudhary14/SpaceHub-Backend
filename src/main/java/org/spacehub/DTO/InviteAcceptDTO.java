package org.spacehub.DTO;

import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InviteAcceptDTO {
  private String type;
  private UUID id;
  private String inviteCode;
  private String acceptorEmail;
}
