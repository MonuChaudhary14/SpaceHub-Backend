package org.spacehub.service.service_auth.authInterfaces;

import org.spacehub.entities.User.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public interface IUserService {

  UserDetails loadUserByUsername(String email) throws UsernameNotFoundException;

  User getUserByEmail(String email);

  void save(User user);

  boolean existsByEmail(String email);
}

