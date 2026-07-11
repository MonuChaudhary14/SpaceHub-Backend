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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
    return path.startsWith("/chat") || path.startsWith("/ws") || path.startsWith("/files/") ||
      path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.startsWith("/notification");
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
    } 
    else if (request.getCookies() != null) {
      for (Cookie cookie : request.getCookies()) {
        if ("accessToken".equals(cookie.getName())) {
          token = cookie.getValue();
          break;
        }
      }
    }

    try {
      if (token != null) {
        userEmail = usernameService.extractUsername(token);
      }
    } catch (Exception ignored) {
      SecurityContextHolder.clearContext();
    }

    if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      try {
        UserDetails userDetails = userService.loadUserByUsername(userEmail);
        User user = (User) userDetails;

        Claims claims = usernameService.extractClaim(token, Function.identity());
        Integer tokenVersionObj = (Integer) claims.get("passwordVersion");
        int tokenVersion = tokenVersionObj != null ? tokenVersionObj : 0;
        int userVersion = user.getPasswordVersion() != null ? user.getPasswordVersion() : 0;

        if (usernameService.validToken(token, user) && tokenVersion == userVersion) {
          UsernamePasswordAuthenticationToken authToken =
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
          authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(authToken);
        }
      } catch (UsernameNotFoundException ignored) {
        SecurityContextHolder.clearContext();
      } catch (Exception ignored) {
        SecurityContextHolder.clearContext();
      }
    }

    filterChain.doFilter(request, response);
  }
}
