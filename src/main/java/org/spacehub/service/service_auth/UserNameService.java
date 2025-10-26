package org.spacehub.service.service_auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.spacehub.entities.User.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;

@Service
public class UserNameService {

  @Value("${SECRET_KEY}")
  private String secretKey;

  private SecretKey getSigningKey() {
    byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
    return Keys.hmacShaKeyFor(keyBytes);
  }

  public String generateToken(UserDetails userDetails) {

    long nowMillis = System.currentTimeMillis();
    long expMillis = nowMillis + 1000L * 60 * 60 * 24;

    int passwordVersion = 0;
    if (userDetails instanceof User) {
      Integer version = ((User) userDetails).getPasswordVersion();
      if (version != null) {
        passwordVersion = version;
      }
    }

    return Jwts.builder()
            .claim("sub", userDetails.getUsername())
            .claim("passwordVersion", passwordVersion)
            .claim("iat", nowMillis / 1000L)
            .claim("exp", expMillis / 1000L)
            .signWith(getSigningKey())
            .compact();

  }

  public String generateRegistrationToken(String email) {
    long nowMillis = System.currentTimeMillis();
    long expMillis = nowMillis + 1000L * 60 * 5;

    return Jwts.builder()
            .claim("sub", email)
            .claim("purpose", "registration_session")
            .claim("iat", nowMillis / 1000L)
            .claim("exp", expMillis / 1000L)
            .signWith(getSigningKey())
            .compact();
  }

  public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    Claims claims = Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    return claimsResolver.apply(claims);
  }

  public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  public boolean validToken(String token, UserDetails userDetails) {
    final String username = extractUsername(token);
    return username != null && username.equals(userDetails.getUsername()) && !isTokenExpired(token);
  }

  private boolean isTokenExpired(String token) {
    Date expiration = extractClaim(token, Claims::getExpiration);
    return expiration.before(new Date());
  }
}
