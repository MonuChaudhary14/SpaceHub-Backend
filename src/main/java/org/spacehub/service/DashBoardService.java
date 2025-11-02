package org.spacehub.service;

import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.User.User;
import org.spacehub.repository.UserRepository;
import org.spacehub.service.Interface.IDashBoardService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

@Service
public class DashBoardService implements IDashBoardService {

  private final UserRepository userRepository;
  private final S3Service s3Service;

  public DashBoardService(UserRepository userRepository, S3Service s3Service) {
    this.userRepository = userRepository;
    this.s3Service = s3Service;
  }

  public ApiResponse<String> saveUsernameByEmail(String email, String username) {

    if (email == null || email.isBlank() || username == null || username.isBlank()) {
      return new ApiResponse<>(400, "Email and username are required", null);
    }

    try {
      User user = userRepository.findByEmail(email).orElseThrow(() ->
              new RuntimeException("User not found with email: " + email));

      user.setUsername(username);
      userRepository.save(user);

      return new ApiResponse<>(200, "Username updated successfully", username);
    }
    catch (RuntimeException e) {
      return new ApiResponse<>(400, e.getMessage(), null);
    }
    catch (Exception e) {
      return new ApiResponse<>(500, "An unexpected error occurred: " + e.getMessage(), null);
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

}