package org.spacehub.DTO.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {

  private String firstName;
  private String lastName;
  private String bio;
  private String location;
  private String website;
  private String dateOfBirth;
  private Boolean isPrivate;
  private String username;
  private String currentPassword;
  private String newPassword;
  private String newEmail;

}
