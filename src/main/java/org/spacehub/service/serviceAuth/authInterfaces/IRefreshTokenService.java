package org.spacehub.service.serviceAuth.authInterfaces;

import org.spacehub.entities.Auth.RefreshToken;
import org.spacehub.entities.User.User;

public interface IRefreshTokenService {

  RefreshToken createRefreshToken(User user);

  boolean deleteIfExists(String token);
}

