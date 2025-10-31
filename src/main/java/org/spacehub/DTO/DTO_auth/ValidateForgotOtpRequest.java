package org.spacehub.DTO.DTO_auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ValidateForgotOtpRequest {
  private String identifier;
  private String otp;

  public ValidateForgotOtpRequest(String identifier, String otp) {
    this.identifier = identifier;
    this.otp = otp;
  }
}
