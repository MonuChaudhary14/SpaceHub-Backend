package org.spacehub.entities.Auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegistrationRequest {

  @NotBlank(message = "First name is required")
  @Size(min = 1, max = 50, message = "First name must be between 1 and 50 characters")
  @Pattern(regexp = "^[A-Za-z]+$", message = "First name must contain only letters and no spaces")
  private String firstName;

  @NotBlank(message = "Last name is required")
  @Size(min = 1, max = 50, message = "Last name must be between 1 and 50 characters")
  @Pattern(regexp = "^[A-Za-z]+$", message = "Last name must contain only letters and no spaces")
  private String lastName;

  @Size(max = 50, message = "Email must not exceed 50 characters")
  @Pattern(
    regexp = "^\\s*[^\\s@]+@[^\\s@]+\\.[^\\s@]+\\s*$",
    message = "Invalid email format or contains spaces inside"
  )
  private String email;

  @NotBlank(message = "Password is required")
  @Pattern(
    regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@#$%^&+=!])(?!.*\\s).+$",
    message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, " +
      "one special character, and no spaces"
  )
  @Size(max = 16, message = "Password must not exceed 8 characters")
  private String password;

  private String phoneNumber;
}
