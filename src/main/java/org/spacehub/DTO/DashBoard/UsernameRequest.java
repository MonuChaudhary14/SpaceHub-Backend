package org.spacehub.DTO.DashBoard;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UsernameRequest {

  @NotBlank(message = "Username is required")
  private String username;

  private String dob;
}
