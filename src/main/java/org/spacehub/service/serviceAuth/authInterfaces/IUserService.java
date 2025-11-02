package org.spacehub.service.serviceAuth.authInterfaces;

import org.spacehub.DTO.User.UserSearchDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.User.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public interface IUserService {

  UserDetails loadUserByUsername(String email) throws UsernameNotFoundException;

  User getUserByEmail(String email);

  void save(User user);

  boolean existsByEmail(String email);

  ResponseEntity<ApiResponse<Page<UserSearchDTO>>> searchUsers(String query, Pageable pageable);

}

