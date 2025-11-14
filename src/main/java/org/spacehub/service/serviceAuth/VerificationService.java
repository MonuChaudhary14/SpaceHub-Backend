package org.spacehub.service.serviceAuth;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.DTO_auth.TokenResponse;
import org.spacehub.entities.User.User;
import org.spacehub.service.serviceAuth.authInterfaces.IVerificationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VerificationService implements IVerificationService {

  private final UserNameService userNameService;
  private final RefreshTokenService refreshTokenService;
  private final PasswordEncoder passwordEncoder;

  public boolean checkCredentials(User user, String rawPassword) {
    if (user == null || rawPassword == null) {
      return false;
    }
    return passwordEncoder.matches(rawPassword, user.getPassword());
  }

  public TokenResponse generateTokens(User user) {
    String accessToken = userNameService.generateToken(user);

    var refreshTokenEntity = refreshTokenService.createRefreshToken(user);
    String refreshToken = refreshTokenEntity.getToken();

    return new TokenResponse(accessToken, refreshToken);
  }
}
