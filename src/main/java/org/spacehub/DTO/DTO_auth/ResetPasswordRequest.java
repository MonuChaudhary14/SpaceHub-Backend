package org.spacehub.DTO.DTO_auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {
  private String identifier;
  private String newPassword;
  private String tempToken;

  public ResetPasswordRequest(String identifier, String newPassword, String tempToken) {
    this.identifier = identifier;
    this.newPassword = newPassword;
    this.tempToken = tempToken;
  }
}
