package org.spacehub.service.service_auth.authInterfaces;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.function.Function;

public interface IUserNameService {

  String generateToken(UserDetails userDetails);

  String generateRegistrationToken(String email);

  <T> T extractClaim(String token, Function<Claims, T> claimsResolver);

  String extractUsername(String token);

  boolean validToken(String token, UserDetails userDetails);
}

