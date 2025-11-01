package org.spacehub.service.serviceAuth;

import org.spacehub.DTO.DTO_auth.TokenResponse;
import org.spacehub.entities.User.User;
import org.spacehub.service.serviceAuth.authInterfaces.IVerificationService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

@Service
public class VerificationService implements IVerificationService {

  private final UserNameService userNameService;
  private final AuthenticationManager authenticationManager;
  private final RefreshTokenService refreshTokenService;
  private final UserService userService;

  public VerificationService(UserNameService userNameService,
                             AuthenticationManager authenticationManager,
                             RefreshTokenService refreshTokenService,
                             UserService userService) {
    this.userNameService = userNameService;
    this.authenticationManager = authenticationManager;
    this.refreshTokenService = refreshTokenService;
    this.userService = userService;
  }

  public boolean checkCredentials(String email, String password) {
    try {
      Authentication authentication = authenticationManager
              .authenticate(new UsernamePasswordAuthenticationToken(email, password));
      return authentication.isAuthenticated();
    }
    catch (Exception e) {
      return false;
    }
  }

  public TokenResponse generateTokens(User user) {
    UserDetails userDetails = userService.loadUserByUsername(user.getEmail());
    String accessToken = userNameService.generateToken(userDetails);

    var refreshTokenEntity = refreshTokenService.createRefreshToken(user);
    String refreshToken = refreshTokenEntity.getToken();

    return new TokenResponse(accessToken, refreshToken);
  }
}
