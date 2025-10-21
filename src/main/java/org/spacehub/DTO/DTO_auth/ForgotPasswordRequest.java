
package org.spacehub.DTO.DTO_auth;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ForgotPasswordRequest {
  private String email;

  public ForgotPasswordRequest(String email) {
    this.email = email;
  }

}
