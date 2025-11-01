package org.spacehub.service.service_auth.authInterfaces;

import org.spacehub.DTO.DTO_auth.TokenResponse;
import org.spacehub.entities.User.User;

public interface IVerificationService {

  boolean checkCredentials(String email, String password);

  TokenResponse generateTokens(User user);

}

