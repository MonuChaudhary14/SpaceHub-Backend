package org.spacehub.service;

import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.User.User;
import org.spacehub.repository.UserRepository;
import org.spacehub.service.Interface.IDashBoardService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Service
public class DashBoardService implements IDashBoardService {

  private final UserRepository userRepository;
  private final S3Service s3Service;

  public DashBoardService(UserRepository userRepository, S3Service s3Service) {
    this.userRepository = userRepository;
    this.s3Service = s3Service;
  }

  public ApiResponse<String> saveUsernameByEmail(String email, String username) {

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
        "Username already takeN.", null);

    } catch (Exception e) {
      return new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(),
        "An unexpected error occurred: " + e.getMessage(), null);
    }
  }

  public ApiResponse<String> uploadProfileImage(String email, MultipartFile image) {

    if (image == null || image.isEmpty()) {
      return new ApiResponse<>(400, "No image provided", null);
    }

    try {
      User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));

      validateImage(image);

      String fileName = System.currentTimeMillis() + "_" + image.getOriginalFilename();
      String key = "avatars/" + email.replaceAll("[^a-zA-Z0-9]", "_") + "/" + fileName;

      s3Service.uploadFile(key, image.getInputStream(), image.getSize());

      user.setAvatarUrl(key);
      userRepository.save(user);

      String previewUrl = s3Service.generatePresignedDownloadUrl(key, Duration.ofHours(2));

      return new ApiResponse<>(HttpStatus.OK.value(), "Profile image uploaded successfully", previewUrl);

    }
    catch (IOException e) {
      return new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error uploading image: " +
              e.getMessage(), null);
    }
    catch (RuntimeException e) {
      return new ApiResponse<>(400, e.getMessage(), null);
    }
    catch (Exception e) {
      return new ApiResponse<>(500, "An unexpected error occurred: " + e.getMessage(), null);
    }

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

  public ApiResponse<Map<String, Object>> getUserProfileSummary(String email) {
    if (email == null || email.isBlank()) {
      return new ApiResponse<>(400, "Email is required", null);
    }

    try {
      User user = userRepository.findByEmail(email)
              .orElseThrow(() -> new RuntimeException("User not found"));

      String avatarUrl = user.getAvatarUrl();
      String presignedUrl = null;

      if (avatarUrl != null && !avatarUrl.isBlank()) {
        presignedUrl = s3Service.generatePresignedDownloadUrl(avatarUrl, Duration.ofHours(2));
      }

      assert presignedUrl != null;
      Map<String, Object> data = Map.of(
              "username", user.getUsername(),
              "profileImage", presignedUrl
      );

      return new ApiResponse<>(200, "User profile fetched successfully", data);
    }
    catch (RuntimeException e) {
      return new ApiResponse<>(400, e.getMessage(), null);
    }
    catch (Exception e) {
      return new ApiResponse<>(500, "An unexpected error occurred: " + e.getMessage(), null);
    }
  }

}
