package org.spacehub.DTO_auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResendForgotOtpRequest {
  private String tempToken;
}
