package org.spacehub.DTO.presence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPresenceDTO {
  private String email;
  private String status;
  private Long lastActiveEpochMs;
  private Long communityId;
}
