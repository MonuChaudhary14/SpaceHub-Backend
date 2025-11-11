package org.spacehub.service.Profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.DTO.User.DeleteAccount;
import org.spacehub.DTO.User.UserProfileDTO;
import org.spacehub.DTO.User.UserProfileResponse;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.LocalGroup.LocalGroup;
import org.spacehub.entities.User.User;
import org.spacehub.repository.Notification.NotificationRepository;
import org.spacehub.repository.User.RefreshTokenRepository;
import org.spacehub.repository.ChatRoom.ScheduledMessageRepository;
import org.spacehub.repository.User.UserRepository;
import org.spacehub.repository.community.CommunityRepository;
import org.spacehub.repository.community.CommunityUserRepository;
import org.spacehub.repository.localgroup.LocalGroupRepository;
import org.spacehub.service.Interface.IProfileService;
import org.spacehub.service.File.S3Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProfileService implements IProfileService {

  private final UserRepository userRepository;
  private final S3Service s3Service;
  private final CommunityRepository communityRepository;
  private final CommunityUserRepository communityUserRepository;
  private final LocalGroupRepository localGroupRepository;
  private static final Logger log = LoggerFactory.getLogger(ProfileService.class);
  private final NotificationRepository notificationRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final ScheduledMessageRepository scheduledMessageRepository;

  public ProfileService(UserRepository userRepository, S3Service s3Service,
                        CommunityRepository communityRepository,
                        CommunityUserRepository communityUserRepository,
                        LocalGroupRepository localGroupRepository, NotificationRepository notificationRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        ScheduledMessageRepository scheduledMessageRepository) {
    this.userRepository = userRepository;
    this.s3Service = s3Service;
    this.communityRepository = communityRepository;
    this.communityUserRepository = communityUserRepository;
    this.localGroupRepository = localGroupRepository;
    this.notificationRepository = notificationRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.scheduledMessageRepository = scheduledMessageRepository;
  }

  @Override
  public UserProfileResponse getProfileByEmail(String email) {
    if (email == null || email.isBlank()) throw new IllegalArgumentException("Email is required");
    User user = userRepository.findByEmail(email.trim().toLowerCase())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    return buildResponse(user);
  }

  @Override
  public UserProfileResponse updateProfileByEmail(String email, UserProfileDTO dto) {
    validateInput(email, dto);

    User user = userRepository.findByEmail(email.trim().toLowerCase())
      .orElseThrow(() -> new IllegalArgumentException("User not found"));

    try {
      updateBasicDetails(user, dto);
      updatePasswordIfNeeded(user, dto);

      userRepository.save(user);
      log.info("Profile updated successfully for user: {}", email);
      return buildResponse(user);
    }
    catch (Exception e) {
      log.error("Failed to update profile for {}: {}", email, e.getMessage());
      throw e;
    }
  }


  @Override
  public UserProfileResponse uploadAvatarByEmail(String email, MultipartFile file) {
    if (email == null || email.isBlank()) throw new IllegalArgumentException("Email is required");
    validateImage(file);

    User user = userRepository.findByEmail(email.trim().toLowerCase())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    String key = "avatars/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
    try {
      s3Service.uploadFile(key, file.getInputStream(), file.getSize());
      user.setAvatarUrl(key);
      userRepository.save(user);
      return buildResponse(user);
    }
    catch (Exception e) {
      log.error("Avatar upload failed for {}: {}", email, e.getMessage());
      throw new RuntimeException("Avatar upload failed, please try again.");
    }
  }

  @Override
  public UserProfileResponse uploadCoverPhotoByEmail(String email, MultipartFile file) {
    if (email == null || email.isBlank()) throw new IllegalArgumentException("Email is required");
    validateImage(file);

    User user = userRepository.findByEmail(email.trim().toLowerCase())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    String key = "covers/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
    try {
      s3Service.uploadFile(key, file.getInputStream(), file.getSize());
      user.setCoverPhotoUrl(key);
      userRepository.save(user);
      return buildResponse(user);
    }
    catch (Exception e) {
      log.error("Cover photo upload failed for {}: {}", email, e.getMessage());
      throw new RuntimeException("Cover photo upload failed, please try again.");
    }
  }

  @Override
  @Transactional
  public void deleteAccount(DeleteAccount request) {
    String email = request.getEmail();
    String currentPassword = request.getCurrentPassword();

    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("Email is required");
    }
    if (currentPassword == null || currentPassword.isBlank()) {
      throw new IllegalArgumentException("Current password is required");
    }

    User user = userRepository.findByEmail(email.trim().toLowerCase())
      .orElseThrow(() -> new IllegalArgumentException("User not found"));

    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    if (!encoder.matches(currentPassword, user.getPassword())) {
      throw new SecurityException("Incorrect password");
    }

    try {

      notificationRepository.deleteAllBySender(user);
      notificationRepository.deleteAllByRecipient(user);

      refreshTokenRepository.deleteAllByUser(user);

      scheduledMessageRepository.deleteAllBySenderEmail(user.getEmail());

      removeUserFromCommunities(user);

      removeUserFromGroups(user);

      communityUserRepository.deleteByUserId(user.getId());

      safeDelete(user.getAvatarUrl());
      safeDelete(user.getCoverPhotoUrl());

      userRepository.delete(user);

    } catch (Exception e) {
      throw new RuntimeException("Account deletion failed, please try again later.");
    }
  }

  private void validateImage(MultipartFile file) {
    if (file.isEmpty()) throw new IllegalArgumentException("File cannot be empty");
    if (file.getSize() > 2 * 1024 * 1024)
      throw new IllegalArgumentException("File size exceeds 2 MB");
    if (file.getContentType() == null || !file.getContentType().startsWith("image/"))
      throw new IllegalArgumentException("Only image files are allowed");
  }

  private UserProfileResponse buildResponse(User user) {
    UserProfileResponse resp = new UserProfileResponse();
    resp.setFirstName(user.getFirstName());
    resp.setLastName(user.getLastName());
    resp.setUsername(user.getUsername());
    resp.setBio(user.getBio());
    resp.setDateOfBirth(user.getDateOfBirth());
    if (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
      resp.setAvatarPreviewUrl(s3Service.generatePresignedDownloadUrl(user.getAvatarUrl(),
        Duration.ofMinutes(30)));
    }
    return resp;
  }

  private void removeUserFromCommunities(User user) {
    List<Community> createdCommunities = communityRepository.findAllByCreatedByWithUsers(user);
    for (Community c : createdCommunities) {
      c.setCreatedBy(null);
      c.getCommunityUsers().removeIf(cu -> cu.getUser().getId().equals(user.getId()));
      communityRepository.save(c);
    }

    List<Community> pendingIn = communityRepository.findAllWithPendingUser(user);
    for (Community c : pendingIn) {
      c.getPendingRequests().remove(user);
      communityRepository.save(c);
    }

    List<Community> memberIn = communityRepository.findAllWhereUserIsMember(user);
    for (Community c : memberIn) {
      c.getCommunityUsers().removeIf(cu -> cu.getUser().getId().equals(user.getId()));
      communityRepository.save(c);
    }
  }

  private void removeUserFromGroups(User user) {
    List<LocalGroup> createdGroups = localGroupRepository.findAllByCreatedBy(user);
    for (LocalGroup g : createdGroups) {
      g.setCreatedBy(null);
      g.getMembers().remove(user);
      localGroupRepository.save(g);
    }

    List<LocalGroup> memberIn = localGroupRepository.findAllWhereUserIsMember(user);
    for (LocalGroup g : memberIn) {
      g.getMembers().remove(user);
      localGroupRepository.save(g);
    }
  }

  private void safeDelete(String fileUrl) {
    if (fileUrl == null || fileUrl.isBlank()) return;
    try {
      s3Service.deleteFile(fileUrl);
    } catch (Exception e) {
      log.error("Failed to delete S3 file: {}", fileUrl);
    }
  }

  private void validateInput(String email, UserProfileDTO dto) {
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("Email is required");
    }
    if (dto == null) {
      throw new IllegalArgumentException("Profile data is missing");
    }
  }

  private void updateBasicDetails(User user, UserProfileDTO dto) {
    Optional.ofNullable(dto.getFirstName()).ifPresent(user::setFirstName);
    Optional.ofNullable(dto.getLastName()).ifPresent(user::setLastName);
    Optional.ofNullable(dto.getBio()).ifPresent(user::setBio);
    Optional.ofNullable(dto.getUsername()).ifPresent(user::setUsername);

    Optional.ofNullable(dto.getDateOfBirth())
      .ifPresent(d -> user.setDateOfBirth(LocalDate.parse(d)));
  }

  private void updatePasswordIfNeeded(User user, UserProfileDTO dto) {
    String current = dto.getCurrentPassword();
    String next = dto.getNewPassword();

    if (current == null || next == null || current.isBlank() || next.isBlank()) {
      return;
    }

    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    if (!encoder.matches(current, user.getPassword())) {
      throw new IllegalArgumentException("Current password is incorrect");
    }

    user.setPassword(encoder.encode(next));
    user.setPasswordVersion(Optional.ofNullable(user.getPasswordVersion()).orElse(0) + 1);
  }

}
