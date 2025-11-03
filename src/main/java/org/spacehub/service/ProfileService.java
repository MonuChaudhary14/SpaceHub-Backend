package org.spacehub.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.DTO.User.UserProfileDTO;
import org.spacehub.DTO.User.UserProfileResponse;
import org.spacehub.entities.User.User;
import org.spacehub.repository.UserRepository;
import org.spacehub.repository.community.CommunityRepository;
import org.spacehub.repository.community.CommunityUserRepository;
import org.spacehub.repository.localgroup.LocalGroupRepository;
import org.spacehub.service.Interface.IProfileService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class ProfileService implements IProfileService {

  private final UserRepository userRepository;
  private final S3Service s3Service;
  private final CommunityRepository communityRepository;
  private final CommunityUserRepository communityUserRepository;
  private final LocalGroupRepository localGroupRepository;
  private static final Logger logger = LoggerFactory.getLogger(ProfileService.class);

  public ProfileService(UserRepository userRepository, S3Service s3Service, CommunityRepository communityRepository,
                        CommunityUserRepository communityUserRepository, LocalGroupRepository localGroupRepository) {
    this.userRepository = userRepository;
    this.s3Service = s3Service;
    this.communityRepository = communityRepository;
    this.communityUserRepository = communityUserRepository;
    this.localGroupRepository = localGroupRepository;
  }

  public UserProfileResponse getProfileByEmail(String email) {
    User user = userRepository.findByEmail(email)
      .orElseThrow(() -> new RuntimeException("User not found"));

    UserProfileResponse resp = new UserProfileResponse();
    resp.setId(user.getId());
    resp.setFirstName(user.getFirstName());
    resp.setLastName(user.getLastName());
    resp.setUsername(user.getUsername());
    resp.setEmail(user.getEmail());
    resp.setBio(user.getBio());
    resp.setLocation(user.getLocation());
    resp.setWebsite(user.getWebsite());
    resp.setDateOfBirth(user.getDateOfBirth());
    resp.setIsPrivate(user.getIsPrivate());
    resp.setCreatedAt(user.getCreatedAt());
    resp.setUpdatedAt(user.getUpdatedAt());

    String avatarKey = user.getAvatarUrl();
    String coverKey  = user.getCoverPhotoUrl();
    resp.setAvatarKey(avatarKey);
    resp.setCoverKey(coverKey);

    if (avatarKey != null && !avatarKey.isBlank()) {
      resp.setAvatarPreviewUrl(s3Service.generatePresignedDownloadUrl(avatarKey, Duration.ofMinutes(15)));
    }
    if (coverKey != null && !coverKey.isBlank()) {
      resp.setCoverPreviewUrl(s3Service.generatePresignedDownloadUrl(coverKey, Duration.ofMinutes(15)));
    }

    return resp;
  }

  public User updateProfileByEmail(String email, UserProfileDTO dto) {
    User user = userRepository.findByEmail(email)
      .orElseThrow(() -> new RuntimeException("User not found"));

    applyUserProfileUpdates(user, dto);

    return userRepository.save(user);
  }

  private void applyUserProfileUpdates(User user, UserProfileDTO dto) {
    if (dto == null) return;

    Optional.ofNullable(dto.getFirstName()).ifPresent(user::setFirstName);
    Optional.ofNullable(dto.getLastName()).ifPresent(user::setLastName);
    Optional.ofNullable(dto.getBio()).ifPresent(user::setBio);
    Optional.ofNullable(dto.getLocation()).ifPresent(user::setLocation);
    Optional.ofNullable(dto.getWebsite()).ifPresent(user::setWebsite);
    Optional.ofNullable(dto.getDateOfBirth()).ifPresent(date -> user.setDateOfBirth(LocalDate.parse(date)));
    Optional.ofNullable(dto.getIsPrivate()).ifPresent(user::setIsPrivate);
    Optional.ofNullable(dto.getUsername()).ifPresent(user::setUsername);
  }


  public User uploadAvatarByEmail(String email, MultipartFile file) throws IOException {
    validateImage(file);

    User user = userRepository.findByEmail(email)
      .orElseThrow(() -> new RuntimeException("User not found"));

    String key = "avatars/" + file.getOriginalFilename();
    s3Service.uploadFile(key, file.getInputStream(), file.getSize());

    user.setAvatarUrl(key);
    return userRepository.save(user);
  }

  public User uploadCoverPhotoByEmail(String email, MultipartFile file) throws IOException {
    validateImage(file);

    User user = userRepository.findByEmail(email)
      .orElseThrow(() -> new RuntimeException("User not found"));

    String key = "covers/" + file.getOriginalFilename();
    s3Service.uploadFile(key, file.getInputStream(), file.getSize());

    user.setCoverPhotoUrl(key);
    return userRepository.save(user);
  }

  private void validateImage(MultipartFile file) {
    if (file.isEmpty()) {
      throw new RuntimeException("File is empty");
    }
    if (file.getSize() > 2 * 1024 * 1024) {
      throw new RuntimeException("File size exceeds 2 MB");
    }
    String contentType = file.getContentType();
    if (contentType == null || !contentType.startsWith("image/")) {
      throw new RuntimeException("Only image files are allowed");
    }
  }

  public User updateAccount(
    String email,
    MultipartFile avatarFile,
    String newUsername,
    String newEmail,
    String currentPassword,
    String newPassword
  ) throws Exception {

    validateEmail(email);

    User user = findUserByEmail(email);

    updateUsername(user, newUsername);
    updateEmail(user, newEmail);
    updatePassword(user, currentPassword, newPassword);
    updateAvatar(user, avatarFile);

    return userRepository.save(user);
  }

  private void validateEmail(String email) {
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("email (current) is required");
    }
  }

  private User findUserByEmail(String email) {
    String normalized = email.trim().toLowerCase();
    return userRepository.findByEmail(normalized)
      .orElseThrow(() -> new RuntimeException("User not found"));
  }

  private void updateUsername(User user, String newUsername) {
    if (newUsername == null || newUsername.isBlank()) return;

    String normalized = newUsername.trim();
    if (!normalized.equals(user.getUsername())) {
      if (userRepository.existsByUsername(normalized)) {
        throw new IllegalArgumentException("Username already in use");
      }
      user.setUsername(normalized);
    }
  }

  private void updateEmail(User user, String newEmail) {
    if (newEmail == null || newEmail.isBlank()) return;

    String normalized = newEmail.trim().toLowerCase();
    if (!normalized.equals(user.getEmail())) {
      if (userRepository.existsByEmail(normalized)) {
        throw new IllegalArgumentException("Email already in use");
      }
      user.setEmail(normalized);
    }
  }

  private void updatePassword(User user, String currentPassword, String newPassword) {
    if (newPassword == null || newPassword.isBlank()) return;

    if (currentPassword == null || currentPassword.isBlank()) {
      throw new IllegalArgumentException("Current password is required to change password");
    }

    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    if (!encoder.matches(currentPassword, user.getPassword())) {
      throw new IllegalArgumentException("Current password is incorrect");
    }

    user.setPassword(encoder.encode(newPassword));
    user.setPasswordVersion(Optional.ofNullable(user.getPasswordVersion()).orElse(0) + 1);
  }

  private void updateAvatar(User user, MultipartFile avatarFile) throws IOException {
    if (avatarFile == null || avatarFile.isEmpty()) return;

    validateImage(avatarFile);
    String fileName = System.currentTimeMillis() + "_" + avatarFile.getOriginalFilename();

    String userIdentifier = user.getId() != null
      ? user.getId().toString()
      : user.getEmail().replaceAll("[^a-zA-Z0-9]", "_");

    String key = String.format("avatars/%s/%s", userIdentifier, fileName);
    s3Service.uploadFile(key, avatarFile.getInputStream(), avatarFile.getSize());
    user.setAvatarUrl(key);
  }


  public void deleteAccount(String email, String currentPassword) {
    validateInputs(email, currentPassword);

    User user = getUserByEmail(email);
    verifyPassword(user, currentPassword);

    removeUserFromCommunities(user);
    removeUserFromGroups(user);

    communityUserRepository.deleteByUserId(user.getId());

    safeDelete(user.getAvatarUrl());
    safeDelete(user.getCoverPhotoUrl());

    userRepository.delete(user);
  }

  private void validateInputs(String email, String currentPassword) {
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("Email is required");
    }
    if (currentPassword == null || currentPassword.isBlank()) {
      throw new IllegalArgumentException("Current password is required to delete account");
    }
  }

  private User getUserByEmail(String email) {
    return userRepository.findByEmail(email.trim().toLowerCase())
      .orElseThrow(() -> new IllegalArgumentException("User not found"));
  }

  private void verifyPassword(User user, String currentPassword) {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    if (!encoder.matches(currentPassword, user.getPassword())) {
      throw new SecurityException("Current password is incorrect");
    }
  }

  private void removeUserFromCommunities(User user) {
    communityRepository.findAll().forEach(c -> {
      boolean changed = c.getPendingRequests() != null &&
        c.getPendingRequests().removeIf(u -> u != null && u.getId() != null && u.getId().equals(user.getId()));

      if (c.getCommunityUsers() != null &&
        c.getCommunityUsers().removeIf(cu -> cu.getUser() != null &&
          cu.getUser().getId().equals(user.getId()))) {
        changed = true;
      }

      if (changed) communityRepository.save(c);
    });
  }

  private void removeUserFromGroups(User user) {
    localGroupRepository.findAll().forEach(g -> {
      if (g.getMembers() != null &&
        g.getMembers().removeIf(m -> m != null && m.getId() != null && m.getId().equals(user.getId()))) {
        localGroupRepository.save(g);
      }
    });
  }

  private void safeDelete(String fileUrl) {
    if (fileUrl == null) return;
    try {
      s3Service.deleteFile(fileUrl);
      logger.info("Deleted file from S3: {}", fileUrl);
    } catch (Exception e) {
      logger.error("Failed to delete file from S3: {}", fileUrl, e);
    }
  }

}
