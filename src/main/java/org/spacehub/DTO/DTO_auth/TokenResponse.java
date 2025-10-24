package org.spacehub.DTO.DTO_auth;

import lombok.Data;

@Data
public class TokenResponse {
  private String accessToken;
  private String refreshToken;

  public TokenResponse(String accessToken, String refreshToken) {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
  }

}
