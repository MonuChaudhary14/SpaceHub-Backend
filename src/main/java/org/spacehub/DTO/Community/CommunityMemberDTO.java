package org.spacehub.DTO.Community;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.spacehub.entities.Community.Role;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityMemberDTO {

  private UUID memberId;
  private String username;
  private String email;
  private Role role;
  private LocalDateTime joinDate;
  private boolean isBanned;
  private String avatarKey;
  private String avatarPreviewUrl;
  private String bio;
  private String location;
  private String website;

}
