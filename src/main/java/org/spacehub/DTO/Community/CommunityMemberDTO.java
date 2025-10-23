package org.spacehub.DTO.Community;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.spacehub.entities.Community.Role;
import org.spacehub.entities.User.User;
import org.spacehub.entities.User.UserRole;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityMemberDTO {

    private Long memberId;
    private String username;
    private String email;
    private Role role;
    private LocalDateTime joinDate;
    private boolean isBanned;

    public CommunityMemberDTO(User user) {
        this.memberId = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.joinDate = user.getCreatedAt();
        this.isBanned = false;
    }
}
