package org.spacehub.service.serviceAuth;

import org.spacehub.DTO.User.UserSearchDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.User.User;
import org.spacehub.repository.UserRepository;
import org.spacehub.service.S3Service;
import org.spacehub.service.serviceAuth.authInterfaces.IUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class UserService implements UserDetailsService, IUserService {

  private final UserRepository userRepository;
  private final S3Service s3Service;

  public UserService(UserRepository userRepository, S3Service s3Service) {
    this.userRepository = userRepository;
    this.s3Service = s3Service;
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    return userRepository.findByEmail(email)
      .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
  }

  public User getUserByEmail(String email) {
    return userRepository.findByEmail(email)
      .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
  }

  public void save(User user) {
    userRepository.save(user);
  }

  public boolean existsByEmail(String email) {
    return userRepository.existsByEmail(email);
  }

  public ResponseEntity<ApiResponse<Page<UserSearchDTO>>> searchUsers(String query, Pageable pageable) {
    if (query == null || query.isBlank()) {
      return ResponseEntity.badRequest()
        .body(new ApiResponse<>(400, "Search query is required", Page.empty(pageable)));
    }

    try {
      Page<User> userPage = userRepository.findByUsernameContainingIgnoreCase(query, pageable);

      Page<UserSearchDTO> dtoPage = userPage.map(user -> {
        String avatarUrl = null;
        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
          avatarUrl = s3Service.generatePresignedDownloadUrl(user.getAvatarUrl(), Duration.ofHours(2));
        }
        return new UserSearchDTO(
          user.getId(),
          user.getUsername(),
          user.getEmail(),
          avatarUrl
        );
      });

      return ResponseEntity.ok(
        new ApiResponse<>(200, "Users retrieved successfully", dtoPage)
      );

    } catch (Exception e) {
      return ResponseEntity.internalServerError()
        .body(new ApiResponse<>(500, "An error occurred: " + e.getMessage(), null));
    }
  }

}
