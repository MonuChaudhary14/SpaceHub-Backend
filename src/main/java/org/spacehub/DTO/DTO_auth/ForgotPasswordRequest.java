
package org.spacehub.DTO.DTO_auth;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ForgotPasswordRequest {
  private String identifier;

  public ForgotPasswordRequest(String identifier) {
    this.identifier = identifier;
  }

}
