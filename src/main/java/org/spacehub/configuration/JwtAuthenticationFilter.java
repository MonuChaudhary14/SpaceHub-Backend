package org.spacehub.configuration;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.spacehub.entities.User.User;
import org.spacehub.service.serviceAuth.UserNameService;
import org.spacehub.service.serviceAuth.UserService;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.function.Function;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final UserNameService usernameService;
  private final UserService userService;

  public JwtAuthenticationFilter(UserNameService usernameService, UserService userService) {
    this.usernameService = usernameService;
    this.userService = userService;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    String path = request.getRequestURI();
    return path.startsWith("/swagger-ui") ||
      path.startsWith("/v3/api-docs") ||
      path.startsWith("/notification") ||
      path.startsWith("/notifications") ||
      path.startsWith("/chat") ||
      path.startsWith("/ws");
  }

  @Override
  protected void doFilterInternal(
    @NonNull HttpServletRequest request,
    @NonNull HttpServletResponse response,
    @NonNull FilterChain filterChain) throws ServletException, IOException {

    String token = extractToken(request);
    String userEmail = extractEmailFromToken(token);

    if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      authenticateUser(token, userEmail, request);
    }

    filterChain.doFilter(request, response);
  }

  private String extractToken(HttpServletRequest request) {
    final String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      return header.substring(7);
    }
    if (request.getCookies() != null) {
      for (Cookie cookie : request.getCookies()) {
        if ("accessToken".equals(cookie.getName())) {
          return cookie.getValue();
        }
      }
    }
    return null;
  }

  private String extractEmailFromToken(String token) {
    if (token == null) {
      return null;
    }
    try {
      return usernameService.extractUsername(token);
    }
    catch (Exception ignored) {
      SecurityContextHolder.clearContext();
      return null;
    }
  }

  private void authenticateUser(String token, String userEmail, HttpServletRequest request) {
    try {
      UserDetails userDetails = userService.loadUserByUsername(userEmail);
      User user = (User) userDetails;

      if (isTokenValid(token, user)) {
        UsernamePasswordAuthenticationToken authToken =
          new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
      }
    }
    catch (Exception ignored) {
      SecurityContextHolder.clearContext();
    }
  }

  private boolean isTokenValid(String token, User user) {
    if (!usernameService.validToken(token, user)) {
      return false;
    }
    Claims claims = usernameService.extractClaim(token, Function.identity());
    Integer tokenVersionObj = (Integer) claims.get("passwordVersion");
    int tokenVersion = tokenVersionObj != null ? tokenVersionObj : 0;
    int userVersion = user.getPasswordVersion() != null ? user.getPasswordVersion() : 0;
    return tokenVersion == userVersion;
  }
}
