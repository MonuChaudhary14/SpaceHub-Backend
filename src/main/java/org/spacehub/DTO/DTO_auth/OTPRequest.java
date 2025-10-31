package org.spacehub.DTO.DTO_auth;

import lombok.Data;
import org.spacehub.entities.OTP.OtpType;

@Data
public class OTPRequest {

  private String identifier;
  private String otp;
  private OtpType type;

}
