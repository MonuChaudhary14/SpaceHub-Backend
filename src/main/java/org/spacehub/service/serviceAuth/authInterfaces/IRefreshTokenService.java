package org.spacehub.service.serviceAuth.authInterfaces;

import jakarta.servlet.http.HttpServletRequest;
import org.spacehub.DTO.DTO_auth.TokenResponse;
import org.spacehub.entities.Auth.RefreshToken;
import org.spacehub.entities.User.User;

public interface IRefreshTokenService {

  RefreshToken createRefreshToken(User user);

  RefreshToken createRefreshToken(User user, HttpServletRequest request);

  TokenResponse rotateRefreshToken(String rawToken, HttpServletRequest request);

  void revokeTokenFamily(String familyId);

  void revokeAllUserTokens(User user);

  boolean deleteIfExists(String token);
}


