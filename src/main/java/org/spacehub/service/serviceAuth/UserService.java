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
import org.spacehub.repository.FriendsRepository;
import org.spacehub.entities.Friends.Friends;
import java.util.Optional;

import java.time.Duration;

@Service
public class UserService implements UserDetailsService, IUserService {

  private final UserRepository userRepository;
  private final S3Service s3Service;
  private final FriendsRepository friendsRepository;

  public UserService(UserRepository userRepository, S3Service s3Service, FriendsRepository friendsRepository) {
    this.userRepository = userRepository;
    this.s3Service = s3Service;
    this.friendsRepository = friendsRepository;
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

  public ResponseEntity<ApiResponse<Page<UserSearchDTO>>> searchUsers(String query, String currentUserEmail, Pageable pageable) {
    if (query == null || query.isBlank()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Search query is required", Page.empty(pageable)));
    }

    try {

      User currentUser = userRepository.findByEmail(currentUserEmail).orElseThrow(() -> new UsernameNotFoundException("Current user not found"));

      Page<User> userPage = userRepository.findByUsernameContainingIgnoreCaseAndEmailNot(query, currentUserEmail, pageable);

      Page<UserSearchDTO> dtoPage = userPage.map(user -> {
        String avatarUrl = null;
        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
          avatarUrl = s3Service.generatePresignedDownloadUrl(user.getAvatarUrl(), Duration.ofHours(2));
        }

        String friendshipStatus = "NONE";
        Optional<Friends> friendRelation1 = friendsRepository.findByUserAndFriend(currentUser, user);
        Optional<Friends> friendRelation2 = friendsRepository.findByUserAndFriend(user, currentUser);

        if (friendRelation1.isPresent()) {
          String status = friendRelation1.get().getStatus();
          if ("ACCEPTED".equalsIgnoreCase(status)) {
            friendshipStatus = "FRIEND";
          }
          else if ("PENDING".equalsIgnoreCase(status)) {
            friendshipStatus = "REQUEST_SENT";
          }
        }
        else if (friendRelation2.isPresent()) {
          String status = friendRelation2.get().getStatus();
          if ("ACCEPTED".equalsIgnoreCase(status)) {
            friendshipStatus = "FRIEND";
          }
          else if ("PENDING".equalsIgnoreCase(status)) {
            friendshipStatus = "REQUEST_RECEIVED";
          }
        }


        return new UserSearchDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                avatarUrl,
                friendshipStatus
        );
      });

      return ResponseEntity.ok(
              new ApiResponse<>(200, "Users retrieved successfully", dtoPage));

    }
    catch (Exception e) {
      return ResponseEntity.internalServerError()
              .body(new ApiResponse<>(500, "An error occurred: " + e.getMessage(), null));
    }
  }

}
