package org.spacehub.DTO.User;

import java.util.UUID;

public record UserSearchDTO(
        UUID userId,
        String username,
        String email,
        String avatarUrl,
        String friendshipStatus) {
}
