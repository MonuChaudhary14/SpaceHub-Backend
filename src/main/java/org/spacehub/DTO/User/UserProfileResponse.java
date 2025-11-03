package org.spacehub.DTO.User;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class UserProfileResponse {
  private UUID id;
  private String firstName;
  private String lastName;
  private String username;
  private String email;
  private String avatarKey;
  private String avatarPreviewUrl;
  private String coverKey;
  private String coverPreviewUrl;
  private String bio;
  private String location;
  private String website;
  private LocalDate dateOfBirth;
  private Boolean isPrivate;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
