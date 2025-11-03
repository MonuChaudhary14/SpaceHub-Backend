package org.spacehub.DTO.Community;

import java.util.UUID;

public record PendingRequestUserDTO(
  UUID userId,
  String username,
  String email
) {
}
