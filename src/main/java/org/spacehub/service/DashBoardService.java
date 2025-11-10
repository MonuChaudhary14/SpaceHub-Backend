package org.spacehub.service;

import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.User.User;
import org.spacehub.repository.UserRepository;
import org.spacehub.service.Interface.IDashBoardService;
import org.spacehub.utils.ImageValidator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class DashBoardService implements IDashBoardService {

  private final UserRepository userRepository;
  private final S3Service s3Service;

  public DashBoardService(UserRepository userRepository, S3Service s3Service) {
    this.userRepository = userRepository;
    this.s3Service = s3Service;
  }

  public ApiResponse<String> saveUsernameByEmail(String email, String username) {

    ApiResponse<String> validationError = validateUsernameInputs(email, username);
    if (validationError != null) return validationError;

    try {
      User user = findUserByEmail(email);
      if (user == null) {
        return new ApiResponse<>(HttpStatus.NOT_FOUND.value(),
          "User not found with email: " + email, null);
      }

      if (isUsernameTakenByAnotherUser(username, user)) {
        return new ApiResponse<>(HttpStatus.CONFLICT.value(),
          "Username already taken. Please choose another.", null);
      }

      user.setUsername(username);
      userRepository.save(user);

      return new ApiResponse<>(HttpStatus.OK.value(),
        "Username updated successfully", username);

    } catch (DataIntegrityViolationException e) {
      return new ApiResponse<>(HttpStatus.CONFLICT.value(),
        "Username already taken.", null);
    } catch (Exception e) {
      return new ApiResponse<>(500,
        "An unexpected error occurred: " + e.getMessage(), null);
    }
  }

  private ApiResponse<String> validateUsernameInputs(String email, String username) {
    if (email == null || email.isBlank()) {
      return new ApiResponse<>(400, "Email is null or blank", null);
    }

    if (username == null || username.isBlank()) {
      return new ApiResponse<>(400, "Username cannot be blank", null);
    }

    if (!username.matches("^[a-zA-Z0-9_.-]{3,20}$")) {
      return new ApiResponse<>(400,
        "Username must be 3–20 characters, and can include letters, numbers, '.', '-', '_'", null);
    }

    return null;
  }

  private boolean isUsernameTakenByAnotherUser(String username, User currentUser) {
    Optional<User> existingUserOpt = userRepository.findByUsernameIgnoreCase(username);
    return existingUserOpt.isPresent() && !existingUserOpt.get().getId().equals(currentUser.getId());
  }

  public ApiResponse<String> uploadProfileImage(String email, MultipartFile image) {

    if (isInvalidEmail(email)) {
      return new ApiResponse<>(400, "Email is required", null);
    }

    if (isInvalidImage(image)) {
      return new ApiResponse<>(400, "No image provided", null);
    }

    try {
      User user = findUserByEmail(email);
      ImageValidator.validate(image);

      String key = buildS3Key(email, image);
      if (!uploadToS3(key, image)) {
        return new ApiResponse<>(500, "Error uploading file to S3", null);
      }

      user.setAvatarUrl(key);
      userRepository.save(user);

      String previewUrl = generatePreviewUrlSafely(key);

      return new ApiResponse<>(200, "Profile image uploaded successfully", previewUrl);

    } catch (RuntimeException e) {
      return new ApiResponse<>(400, e.getMessage(), null);
    } catch (Exception e) {
      return new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null);
    }
  }

  private boolean isInvalidEmail(String email) {
    return email == null || email.isBlank();
  }

  private boolean isInvalidImage(MultipartFile image) {
    return image == null || image.isEmpty();
  }

  private User findUserByEmail(String email) {
    return userRepository.findByEmail(email)
      .orElseThrow(() -> new RuntimeException("User not found"));
  }

  private String buildS3Key(String email, MultipartFile image) {
    String originalFileName = image.getOriginalFilename();
    if (originalFileName == null || originalFileName.isBlank()) {
      originalFileName = "profile.png";
    }
    String fileName = UUID.randomUUID() + "_" + originalFileName.replaceAll("\\s+", "_");
    return "avatars/" + email.replaceAll("[^a-zA-Z0-9]", "_") + "/" + fileName;
  }

  private boolean uploadToS3(String key, MultipartFile image) {
    try {
      s3Service.uploadFile(key, image.getInputStream(), image.getSize());
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private String generatePreviewUrlSafely(String key) {
    try {
      return s3Service.generatePresignedDownloadUrl(key, Duration.ofHours(2));
    } catch (Exception e) {
      return null;
    }
  }

  public ApiResponse<Map<String, Object>> getUserProfileSummary(String email) {
    if (email == null || email.isBlank()) {
      return new ApiResponse<>(400, "Email is required", null);
    }

    try {
      Optional<User> optionalUser = userRepository.findByEmail(email.trim().toLowerCase());
      if (optionalUser.isEmpty()) {
        return new ApiResponse<>(404, "User not found with email: " + email, null);
      }

      // Force fetch fresh user
      User user = optionalUser.get();

      // Reload avatar URL if needed
      String presignedUrl = null;
      if (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
        try {
          presignedUrl = s3Service.generatePresignedDownloadUrl(user.getAvatarUrl(), Duration.ofHours(2));
        } catch (Exception ignored) {}
      }

      Map<String, Object> data = new HashMap<>();
      data.put("username", user.getUsername());
      data.put("profileImage", presignedUrl);

      return new ApiResponse<>(200, "User profile fetched successfully", data);

    } catch (Exception e) {
      return new ApiResponse<>(500, "An unexpected error occurred: " + e.getMessage(), null);
    }
  }


}
