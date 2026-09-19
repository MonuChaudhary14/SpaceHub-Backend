package org.spacehub.service.serviceAuth;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.DTO.DTO_auth.TokenResponse;
import org.spacehub.entities.Auth.RefreshToken;
import org.spacehub.entities.User.User;
import org.spacehub.repository.User.RefreshTokenRepository;
import org.spacehub.service.serviceAuth.authInterfaces.IRefreshTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService implements IRefreshTokenService {

  private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);
  private static final long REFRESH_TOKEN_VALIDITY_DAYS = 30;
  private static final String REDIS_FAMILY_PREFIX = "spacehub:auth:family:";

  private final RefreshTokenRepository refreshTokenRepository;
  private final UserNameService userNameService;

  @Autowired(required = false)
  private StringRedisTemplate redisTemplate;

  @Override
  @Transactional
  public RefreshToken createRefreshToken(User user) {
    return createRefreshToken(user, null);
  }

  @Override
  @Transactional
  public RefreshToken createRefreshToken(User user, HttpServletRequest request) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(REFRESH_TOKEN_VALIDITY_DAYS, ChronoUnit.DAYS);
    String familyId = UUID.randomUUID().toString();

    String ipAddress = extractClientIp(request);
    String userAgent = extractUserAgent(request);

    RefreshToken refreshToken = RefreshToken.builder()
      .token(UUID.randomUUID().toString())
      .familyId(familyId)
      .user(user)
      .isRevoked(false)
      .ipAddress(ipAddress)
      .userAgent(userAgent)
      .createdAt(now)
      .expiresAt(expiresAt)
      .build();

    RefreshToken saved = refreshTokenRepository.save(refreshToken);
    cacheActiveFamilyToken(familyId, saved.getToken(), Duration.ofDays(REFRESH_TOKEN_VALIDITY_DAYS));

    logger.debug("Created new Refresh Token Family [{}] for user [{}]", familyId, user.getEmail());
    return saved;
  }

  @Override
  @Transactional
  public TokenResponse rotateRefreshToken(String rawToken, HttpServletRequest request) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new IllegalArgumentException("Refresh token is required");
    }

    RefreshToken existingToken = refreshTokenRepository.findByToken(rawToken)
      .orElseThrow(() -> new SecurityException("Invalid refresh token"));

    String familyId = existingToken.getFamilyId();
    User user = existingToken.getUser();

    if (Boolean.TRUE.equals(existingToken.getIsRevoked()) || isFamilyRevokedInRedis(familyId)) {
      logger.warn("SECURITY ALERT: Refresh token reuse detected for User [{}] on Family [{}]. Revoking family!",
        user.getEmail(), familyId);
      revokeTokenFamily(familyId);
      throw new SecurityException(
        "Security violation: Refresh token reuse detected. All sessions in this family have been terminated.");
    }

    if (existingToken.getExpiresAt().isBefore(Instant.now())) {
      existingToken.setIsRevoked(true);
      refreshTokenRepository.save(existingToken);
      throw new SecurityException("Refresh token expired");
    }

    Instant now = Instant.now();
    Instant newExpiresAt = now.plus(REFRESH_TOKEN_VALIDITY_DAYS, ChronoUnit.DAYS);
    String newTokenValue = UUID.randomUUID().toString();

    RefreshToken newRefreshToken = RefreshToken.builder()
      .token(newTokenValue)
      .familyId(familyId)
      .user(user)
      .isRevoked(false)
      .ipAddress(extractClientIp(request))
      .userAgent(extractUserAgent(request))
      .createdAt(now)
      .expiresAt(newExpiresAt)
      .build();

    existingToken.setIsRevoked(true);
    existingToken.setReplacedByToken(newTokenValue);

    refreshTokenRepository.save(existingToken);
    RefreshToken savedNewToken = refreshTokenRepository.save(newRefreshToken);

    cacheActiveFamilyToken(familyId, savedNewToken.getToken(), Duration.ofDays(REFRESH_TOKEN_VALIDITY_DAYS));

    String newAccessToken = userNameService.generateToken(user);

    TokenResponse response = new TokenResponse(newAccessToken, savedNewToken.getToken());
    response.setEmail(user.getEmail());

    logger.debug("Successfully rotated refresh token for User [{}] in Family [{}]", user.getEmail(), familyId);
    return response;
  }

  @Override
  @Transactional
  public void revokeTokenFamily(String familyId) {
    if (familyId == null || familyId.isBlank()) {
      return;
    }

    refreshTokenRepository.revokeFamily(familyId);

    if (redisTemplate != null) {
      try {
        redisTemplate.opsForValue().set(
          REDIS_FAMILY_PREFIX + familyId + ":revoked",
          Instant.now().toString(),
          Duration.ofDays(REFRESH_TOKEN_VALIDITY_DAYS)
        );
        redisTemplate.delete(REDIS_FAMILY_PREFIX + familyId + ":active");
      }
      catch (Exception e) {
        logger.warn("Failed to set Redis revocation state for family {}: {}", familyId, e.getMessage());
      }
    }
  }

  @Override
  @Transactional
  public void revokeAllUserTokens(User user) {
    if (user == null || user.getId() == null) {
      return;
    }
    refreshTokenRepository.revokeAllUserTokens(user.getId());
  }

  @Override
  @Transactional
  public boolean deleteIfExists(String token) {
    var opt = refreshTokenRepository.findByToken(token);
    if (opt.isPresent()) {
      RefreshToken refreshToken = opt.get();
      revokeTokenFamily(refreshToken.getFamilyId());
      refreshTokenRepository.delete(refreshToken);
      return true;
    }
    return false;
  }

  private void cacheActiveFamilyToken(String familyId, String tokenValue, Duration ttl) {
    if (redisTemplate != null && familyId != null) {
      try {
        redisTemplate.opsForValue().set(
          REDIS_FAMILY_PREFIX + familyId + ":active",
          tokenValue,
          ttl
        );
      }
      catch (Exception e) {
        logger.warn("Failed to cache active family token in Redis for family {}: {}", familyId, e.getMessage());
      }
    }
  }

  private boolean isFamilyRevokedInRedis(String familyId) {
    if (redisTemplate != null && familyId != null) {
      try {
        return Boolean.TRUE.equals(redisTemplate.hasKey(REDIS_FAMILY_PREFIX + familyId + ":revoked"));
      }
      catch (Exception ignored) {
      }
    }
    return false;
  }

  private String extractClientIp(HttpServletRequest request) {
    if (request == null) {
      return "UNKNOWN";
    }
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr() != null ? request.getRemoteAddr() : "UNKNOWN";
  }

  private String extractUserAgent(HttpServletRequest request) {
    if (request == null) {
      return "UNKNOWN";
    }
    String ua = request.getHeader("User-Agent");
    return ua != null ? (ua.length() > 500 ? ua.substring(0, 500) : ua) : "UNKNOWN";
  }
}

