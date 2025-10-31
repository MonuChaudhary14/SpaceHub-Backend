package org.spacehub.DTO.presence;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresenceMessage {
  private Long communityId;
  private String email;
  private String action;
}
