package org.spacehub.DTO.User;

public record UserSearchDTO(
  Long userId,
  String username,
  String email,
  String avatarUrl
) {
}
