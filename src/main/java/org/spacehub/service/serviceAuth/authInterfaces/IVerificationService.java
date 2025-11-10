package org.spacehub.service.serviceAuth.authInterfaces;

import org.spacehub.DTO.DTO_auth.TokenResponse;
import org.spacehub.entities.User.User;

public interface IVerificationService {
  boolean checkCredentials(User user, String rawPassword);
  TokenResponse generateTokens(User user);
}


