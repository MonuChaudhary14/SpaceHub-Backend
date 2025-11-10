package org.spacehub.DTO.DTO_auth;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TokenResponse {
  private String accessToken;
  private String refreshToken;
  private String email;

  public TokenResponse(String accessToken, String refreshToken) {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
  }

}
