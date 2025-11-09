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
  private String dateOfBirth;
  private String username;
  private String currentPassword;
  private String newPassword;

}
