package org.spacehub.controller;

import org.spacehub.DTO.DTO_auth.RefreshRequest;
import org.spacehub.DTO.DTO_auth.TokenResponse;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.User.User;
import org.spacehub.repository.RefreshTokenRepository;
import org.spacehub.service.service_auth.UserNameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/auth")
public class TokenController {

  private final RefreshTokenRepository refreshTokenRepository;
  private final UserNameService userNameService;

  public TokenController(RefreshTokenRepository refreshTokenRepository,
                         UserNameService userNameService) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.userNameService = userNameService;
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<TokenResponse>> refresh(@RequestBody RefreshRequest req) {
    if (req == null || req.getRefreshToken() == null) {
      return ResponseEntity.status(400).body(new ApiResponse<>(400, "Refresh token required",
        null));
    }

    var opt = refreshTokenRepository.findByToken(req.getRefreshToken());
    if (opt.isEmpty()) {
      return ResponseEntity.status(401).body(new ApiResponse<>(401, "Invalid refresh token",
        null));
    }

    var refreshToken = opt.get();
    if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
      refreshTokenRepository.delete(refreshToken);
      return ResponseEntity.status(401).body(new ApiResponse<>(401, "Refresh token expired",
        null));
    }

    User user = refreshToken.getUser();
    String accessToken = userNameService.generateToken(user);
    TokenResponse tokens = new TokenResponse(accessToken, refreshToken.getToken());
    return ResponseEntity.ok(new ApiResponse<>(200, "Token refreshed", tokens));
  }
}

