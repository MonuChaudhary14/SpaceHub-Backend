package org.spacehub.DTO.DTO_auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidateForgotOtpRequest {
  private String email;
  private String otp;

}
