package org.spacehub.service.service_auth.authInterfaces;

import org.spacehub.entities.Auth.RefreshToken;
import org.spacehub.entities.User.User;

public interface IRefreshTokenService {

  RefreshToken createRefreshToken(User user);

  boolean deleteIfExists(String token);
}

