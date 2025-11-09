package org.spacehub.DTO.DTO_auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {
  private String email;
  private String newPassword;
  private String tempToken;

}
