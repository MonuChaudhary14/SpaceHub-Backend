package org.spacehub.DTO.User;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class UserProfileResponse {
  private String firstName;
  private String lastName;
  private String username;
  private String email;
  private String avatarKey;
  private String avatarPreviewUrl;
  private String bio;
  private LocalDate dateOfBirth;

}
