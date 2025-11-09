package org.spacehub.service;

import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.User.User;
import org.spacehub.repository.UserRepository;
import org.spacehub.service.Interface.IDashBoardService;
import org.spacehub.utils.ImageValidator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DashBoardService implements IDashBoardService {

  private final UserRepository userRepository;
  private final S3Service s3Service;

  public DashBoardService(UserRepository userRepository, S3Service s3Service) {
    this.userRepository = userRepository;
    this.s3Service = s3Service;
  }

  public ApiResponse<String> saveUsernameByEmail(String email, String username) {

    if(email == null || email.isBlank()){
      return new ApiResponse<>(400, "Email is null or blank", null);
    }

    if (username == null || username.isBlank()) {
      return new ApiResponse<>(400, "Username cannot be blank", null);
    }

    if (!username.matches("^[a-zA-Z0-9_.-]{3,20}$")) {
      return new ApiResponse<>(400,"Username must be 3–20 characters, and can include letters, numbers, '.', '-', '_'", null);
    }

    try {
      User user = userRepository.findByEmail(email).orElse(null);

      if (user == null) {
        return new ApiResponse<>(HttpStatus.NOT_FOUND.value(),
          "User not found with email: " + email, null);
      }

      Optional<User> existingUserOpt = userRepository.findByUsernameIgnoreCase(username);

      if (existingUserOpt.isPresent() && !existingUserOpt.get().getId().equals(user.getId())) {
        return new ApiResponse<>(HttpStatus.CONFLICT.value(),
          "Username already taken. Please choose another.", null);
      }

      user.setUsername(username);
      userRepository.save(user);

      return new ApiResponse<>(HttpStatus.OK.value(), "Username updated successfully", username);

    } catch (DataIntegrityViolationException e) {
      return new ApiResponse<>(HttpStatus.CONFLICT.value(),
        "Username already taken.", null);

    } catch (Exception e) {
      return new ApiResponse<>(500,"An unexpected error occurred: " + e.getMessage(), null);
    }
  }

  public ApiResponse<String> uploadProfileImage(String email, MultipartFile image) {

    if (email == null || email.isBlank()) {
      return new ApiResponse<>(400, "Email is required", null);
    }

    if (image == null || image.isEmpty()) {
      return new ApiResponse<>(400, "No image provided", null);
    }

    try {
      User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));

      ImageValidator.validate(image);

      String originalFileName = image.getOriginalFilename();

      if (originalFileName == null || originalFileName.isBlank()) {
        originalFileName = "profile.png";
      }

      String fileName = UUID.randomUUID() + "_" + originalFileName.replaceAll("\\s+", "_");
      String key = "avatars/" + email.replaceAll("[^a-zA-Z0-9]", "_") + "/" + fileName;

      try {
        s3Service.uploadFile(key, image.getInputStream(), image.getSize());
      }
      catch (IOException e) {
        return new ApiResponse<>(500,"Error uploading file to S3: " + e.getMessage(), null);
      }

      user.setAvatarUrl(key);
      userRepository.save(user);

      String previewUrl;
      try {
        previewUrl = s3Service.generatePresignedDownloadUrl(key, Duration.ofHours(2));
      }
      catch (Exception e) {
        previewUrl = null;
      }

      return new ApiResponse<>(200,"Profile image uploaded successfully", previewUrl);

    }
    catch (RuntimeException e) {
      return new ApiResponse<>(400, e.getMessage(), null);
    }
    catch (Exception e) {
      return new ApiResponse<>(500,"Unexpected error: " + e.getMessage(), null);
    }
  }

  public ApiResponse<Map<String, Object>> getUserProfileSummary(String email) {
    if (email == null || email.isBlank()) {
      return new ApiResponse<>(400, "Email is required", null);
    }

    try {
      String normalized = email.trim().toLowerCase();

      Optional<User> optionalUser = userRepository.findByEmail(normalized);
      if (optionalUser.isEmpty()) {
        return new ApiResponse<>(404, "User not found with email: " + normalized, null);
      }

      User user = optionalUser.get();

      String avatarKey = user.getAvatarUrl();
      String presignedUrl = null;

      if (avatarKey != null && !avatarKey.isBlank()) {
        try {
          presignedUrl = s3Service.generatePresignedDownloadUrl(avatarKey, Duration.ofHours(2));
        } catch (Exception ignored) {
        }
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
