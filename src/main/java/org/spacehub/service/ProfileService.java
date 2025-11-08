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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
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

  public ProfileService(UserRepository userRepository, S3Service s3Service,
                        CommunityRepository communityRepository,
                        CommunityUserRepository communityUserRepository,
                        LocalGroupRepository localGroupRepository) {
    this.userRepository = userRepository;
    this.s3Service = s3Service;
    this.communityRepository = communityRepository;
    this.communityUserRepository = communityUserRepository;
    this.localGroupRepository = localGroupRepository;
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
    if (email == null || email.isBlank()) throw new IllegalArgumentException("Email is required");
    if (dto == null) throw new IllegalArgumentException("Profile data is missing");

    User user = userRepository.findByEmail(email.trim().toLowerCase())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    try {
      Optional.ofNullable(dto.getFirstName()).ifPresent(user::setFirstName);
      Optional.ofNullable(dto.getLastName()).ifPresent(user::setLastName);
      Optional.ofNullable(dto.getBio()).ifPresent(user::setBio);
      Optional.ofNullable(dto.getUsername()).ifPresent(user::setUsername);
      Optional.ofNullable(dto.getDateOfBirth()).ifPresent(d -> user.setDateOfBirth(LocalDate.parse(d)));

      if (dto.getCurrentPassword() != null && dto.getNewPassword() != null &&
              !dto.getCurrentPassword().isBlank() && !dto.getNewPassword().isBlank()) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        if (!encoder.matches(dto.getCurrentPassword(), user.getPassword()))
          throw new IllegalArgumentException("Current password is incorrect");
        user.setPassword(encoder.encode(dto.getNewPassword()));
        user.setPasswordVersion(Optional.ofNullable(user.getPasswordVersion()).orElse(0) + 1);
      }

      if (dto.getNewEmail() != null && !dto.getNewEmail().isBlank()) {
        String newEmail = dto.getNewEmail().trim().toLowerCase();
        if (!newEmail.equals(user.getEmail())) {
          if (userRepository.existsByEmail(newEmail))
            throw new IllegalArgumentException("Email already in use");
          user.setEmail(newEmail);
        }
      }

      userRepository.save(user);
      return buildResponse(user);
    } catch (Exception e) {
      log.error("Failed to update profile for {}: {}", email, e.getMessage());
      throw e;
    }
  }

  @Override
  public UserProfileResponse uploadAvatarByEmail(String email, MultipartFile file) throws IOException {
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
    } catch (Exception e) {
      log.error("Avatar upload failed for {}: {}", email, e.getMessage());
      throw new RuntimeException("Avatar upload failed, please try again.");
    }
  }

  @Override
  public UserProfileResponse uploadCoverPhotoByEmail(String email, MultipartFile file) throws IOException {
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
    } catch (Exception e) {
      log.error("Cover photo upload failed for {}: {}", email, e.getMessage());
      throw new RuntimeException("Cover photo upload failed, please try again.");
    }
  }

  @Override
  public void deleteAccount(String email, String currentPassword) {
    if (email == null || email.isBlank()) throw new IllegalArgumentException("Email is required");
    if (currentPassword == null || currentPassword.isBlank())
      throw new IllegalArgumentException("Current password is required");

    User user = userRepository.findByEmail(email.trim().toLowerCase())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    if (!encoder.matches(currentPassword, user.getPassword()))
      throw new SecurityException("Incorrect password");

    try {
      removeUserFromCommunities(user);
      removeUserFromGroups(user);
      communityUserRepository.deleteByUserId(user.getId());
      safeDelete(user.getAvatarUrl());
      safeDelete(user.getCoverPhotoUrl());
      userRepository.delete(user);
      log.info("Account deleted for {}", email);
    } catch (Exception e) {
      log.error("Failed to delete account for {}: {}", email, e.getMessage());
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
    resp.setEmail(user.getEmail());
    resp.setBio(user.getBio());
    resp.setDateOfBirth(user.getDateOfBirth());
    resp.setAvatarKey(user.getAvatarUrl());
    if (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
      resp.setAvatarPreviewUrl(s3Service.generatePresignedDownloadUrl(user.getAvatarUrl(), Duration.ofMinutes(15)));
    }
    return resp;
  }

  private void removeUserFromCommunities(User user) {
    communityRepository.findAll().forEach(c -> {
      boolean changed = c.getPendingRequests() != null &&
              c.getPendingRequests().removeIf(u -> u != null && user.getId().equals(u.getId()));
      if (c.getCommunityUsers() != null &&
              c.getCommunityUsers().removeIf(cu -> cu.getUser() != null && user.getId().equals(cu.getUser().getId())))
        changed = true;
      if (changed) communityRepository.save(c);
    });
  }

  private void removeUserFromGroups(User user) {
    localGroupRepository.findAll().forEach(g -> {
      if (g.getMembers() != null &&
              g.getMembers().removeIf(m -> m != null && user.getId().equals(m.getId())))
        localGroupRepository.save(g);
    });
  }

  private void safeDelete(String fileUrl) {
    if (fileUrl == null) return;
    try {
      s3Service.deleteFile(fileUrl);
    } catch (Exception e) {
      log.error("Failed to delete file: {}", fileUrl);
    }
  }
}
