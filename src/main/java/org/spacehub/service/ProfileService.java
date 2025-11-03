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

    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("email (current) is required");
    }

    String currentEmail = email.trim().toLowerCase();

    User user = userRepository.findByEmail(currentEmail)
      .orElseThrow(() -> new RuntimeException("User not found"));

    if (newUsername != null && !newUsername.isBlank()) {
      String normalizedUsername = newUsername.trim();
      if (!normalizedUsername.equals(user.getUsername())) {
        if (userRepository.existsByUsername(normalizedUsername)) {
          throw new IllegalArgumentException("Username already in use");
        }
        user.setUsername(normalizedUsername);
      }
    }

    if (newEmail != null && !newEmail.isBlank()) {
      String normalizedNewEmail = newEmail.trim().toLowerCase();
      if (!normalizedNewEmail.equals(user.getEmail())) {
        if (userRepository.existsByEmail(normalizedNewEmail)) {
          throw new IllegalArgumentException("Email already in use");
        }
        user.setEmail(normalizedNewEmail);
      }
    }

    if (newPassword != null && !newPassword.isBlank()) {
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

    if (avatarFile != null && !avatarFile.isEmpty()) {
      validateImage(avatarFile);
      String fileName = System.currentTimeMillis() + "_" + avatarFile.getOriginalFilename();
      String userIdentifier;
      if (user.getId() != null) {
        userIdentifier = user.getId().toString();
      } else {
        userIdentifier = user.getEmail().replaceAll("[^a-zA-Z0-9]", "_");
      }
      String key = String.format("avatars/%s/%s", userIdentifier, fileName);
      s3Service.uploadFile(key, avatarFile.getInputStream(), avatarFile.getSize());
      user.setAvatarUrl(key);
    }

    return userRepository.save(user);
  }

  public void deleteAccount(String email, String currentPassword) {
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("email is required");
    }

    String normalized = email.trim().toLowerCase();
    User user = userRepository.findByEmail(normalized)
      .orElseThrow(() -> new IllegalArgumentException("User not found"));

    if (currentPassword != null && !currentPassword.isBlank()) {
      BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
      if (!encoder.matches(currentPassword, user.getPassword())) {
        throw new SecurityException("Current password is incorrect");
      }
    } else {
      throw new IllegalArgumentException("Current password is required to delete account");
    }
    communityRepository.findAll().forEach(c -> {
      if (c.getPendingRequests() != null) {
        boolean removed = c.getPendingRequests().removeIf(u -> u != null && u.getId() != null && u.getId().equals(user.getId()));
        if (removed) communityRepository.save(c);
      }
      if (c.getCommunityUsers() != null) {
        boolean removed = c.getCommunityUsers().removeIf(cu -> cu.getUser() != null && cu.getUser().getId().equals(user.getId()));
        if (removed) communityRepository.save(c);
      }
    });

    localGroupRepository.findAll().forEach(g -> {
      if (g.getMembers() != null) {
        boolean removed = g.getMembers().removeIf(m -> m != null && m.getId() != null && m.getId().equals(user.getId()));
        if (removed) localGroupRepository.save(g);
      }
    });

    communityUserRepository.deleteByUserId(user.getId());
    safeDelete(user.getAvatarUrl());
    safeDelete(user.getCoverPhotoUrl());

    userRepository.delete(user);
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
