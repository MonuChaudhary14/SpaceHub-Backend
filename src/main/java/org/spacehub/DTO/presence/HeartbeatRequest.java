package org.spacehub.DTO.presence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatRequest {
  private String email;
  private Long communityId;
  private Long clientTimestamp;
}
