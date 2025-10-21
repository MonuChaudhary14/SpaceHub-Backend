package org.spacehub.DTO_auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {
  private String email;
  private String newPassword;
  private String tempToken;

  public ResetPasswordRequest(String email, String newPassword, String tempToken) {
    this.email = email;
    this.newPassword = newPassword;
    this.tempToken = tempToken;
  }
}
