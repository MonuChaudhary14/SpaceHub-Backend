package org.spacehub.DTO.presence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresenceMessage {
  private Long communityId;
  private String email;
  private String action;
}
