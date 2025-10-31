package org.spacehub.DTO.DTO_auth;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class LoginRequest {

  private String identifier;
  private String password;

}
