package org.spacehub.DTO.DTO_auth;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class TokenResponse {
  private final String accessToken;
  private final String refreshToken;
  private String email;
}
