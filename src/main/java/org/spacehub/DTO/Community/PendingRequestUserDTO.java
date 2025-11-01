package org.spacehub.DTO.Community;

public record PendingRequestUserDTO(
  Long userId,
  String username,
  String email
) {
}
