package org.spacehub.service;

import org.spacehub.DTO.UserProfileDTO;
import org.spacehub.DTO.UserProfileResponse;
import org.spacehub.entities.User.User;
import org.spacehub.repository.UserRepository;
import org.spacehub.service.Interface.IProfileService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;

@Service
public class ProfileService implements IProfileService {

  private final UserRepository userRepository;
  private final S3Service s3Service;

  public ProfileService(UserRepository userRepository, S3Service s3Service) {
    this.userRepository = userRepository;
    this.s3Service = s3Service;
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

    if (dto.getFirstName() != null) {
      user.setFirstName(dto.getFirstName());
    }
    if (dto.getLastName() != null) {
      user.setLastName(dto.getLastName());
    }
    if (dto.getBio() != null) {
      user.setBio(dto.getBio());
    }
    if (dto.getLocation() != null) {
      user.setLocation(dto.getLocation());
    }
    if (dto.getWebsite() != null) {
      user.setWebsite(dto.getWebsite());
    }
    if (dto.getDateOfBirth() != null) {
      user.setDateOfBirth(LocalDate.parse(dto.getDateOfBirth()));
    }
    if (dto.getIsPrivate() != null) {
      user.setIsPrivate(dto.getIsPrivate());
    }
    if (dto.getUsername() != null) {
      user.setUsername(dto.getUsername());
    }

    return userRepository.save(user);
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

}
