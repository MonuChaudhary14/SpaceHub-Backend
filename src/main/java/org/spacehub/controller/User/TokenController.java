package org.spacehub.controller.User;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.DTO_auth.RefreshRequest;
import org.spacehub.DTO.DTO_auth.TokenResponse;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.serviceAuth.authInterfaces.IRefreshTokenService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class TokenController {

  private final IRefreshTokenService refreshTokenService;

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<TokenResponse>> refresh(
    HttpServletRequest httpRequest,
    @RequestBody RefreshRequest req
  ) {
    if (req == null || req.getRefreshToken() == null || req.getRefreshToken().isBlank()) {
      return ResponseEntity.status(400).body(new ApiResponse<>(400, "Refresh token required", null));
    }

    try {
      TokenResponse tokens = refreshTokenService.rotateRefreshToken(req.getRefreshToken(), httpRequest);
      ResponseCookie cookie = buildAccessTokenCookie(httpRequest, tokens.getAccessToken(), 24 * 60 * 60);

      return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(new ApiResponse<>(200, "Token refreshed successfully", tokens));
    }
    catch (SecurityException e) {
      return ResponseEntity.status(401).body(new ApiResponse<>(401, e.getMessage(), null));
    }
    catch (IllegalArgumentException e) {
      return ResponseEntity.status(400).body(new ApiResponse<>(400, e.getMessage(), null));
    }
    catch (Exception e) {
      return ResponseEntity.status(500).body(new ApiResponse<>(500, "An error occurred while refreshing token", null));
    }
  }

  private ResponseCookie buildAccessTokenCookie(HttpServletRequest request, String value, long maxAgeSeconds) {
    ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from("accessToken", value)
      .httpOnly(true)
      .path("/")
      .maxAge(maxAgeSeconds);

    if (isLocalDevelopment(request)) {
      return builder.secure(false)
        .sameSite("Lax")
        .build();
    }

    return builder.secure(true)
      .sameSite("None")
      .build();
  }

  private boolean isLocalDevelopment(HttpServletRequest request) {
    String origin = request.getHeader("Origin");
    if (origin != null) {
      return origin.contains("localhost") || origin.contains("127.0.0.1") || origin.contains("::1");
    }

    String host = request.getServerName();
    return "localhost".equalsIgnoreCase(host)
      || "127.0.0.1".equals(host)
      || "::1".equals(host);
  }
}
