package org.spacehub.configuration;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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
public class Filters extends OncePerRequestFilter {

  private final UserNameService usernameService;
  private final UserService userService;

  public Filters(UserNameService usernameService, UserService userService) {
    this.usernameService = usernameService;
    this.userService = userService;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/chat") || path.startsWith("/ws") || path.startsWith("/files/") || path.startsWith("/swagger-ui/") ||
            path.startsWith("/v3/api-docs/") || path.startsWith("/api/v1/voice-room/");
  }


  @Override
  protected void doFilterInternal(
    @NonNull HttpServletRequest request,
    @NonNull HttpServletResponse response,
    @NonNull FilterChain filterChain) throws ServletException, IOException {

    final String header = request.getHeader("Authorization");
    String token = null;
    String userEmail = null;

    if (header != null && header.startsWith("Bearer ")) {
      token = header.substring(7);
      userEmail = usernameService.extractUsername(token);
    }

    if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      UserDetails userDetails = userService.loadUserByUsername(userEmail);
      User user = (User) userDetails;

      Claims claims = usernameService.extractClaim(token, Function.identity());
      int tokenVersion = (Integer) claims.get("passwordVersion");

      if (usernameService.validToken(token, user) && tokenVersion == user.getPasswordVersion()) {
        UsernamePasswordAuthenticationToken authToken =
          new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
      }
    }

    filterChain.doFilter(request, response);
  }
}
