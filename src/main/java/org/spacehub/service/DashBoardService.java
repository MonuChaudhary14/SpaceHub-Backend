package org.spacehub.service;

import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.User.User;
import org.spacehub.repository.UserRepository;
import org.spacehub.security.EmailValidator;
import org.spacehub.service.Interface.IDashBoardService;
import org.spacehub.utils.ImageValidator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Service
@Transactional
public class DashBoardService implements IDashBoardService {

  private final UserRepository userRepository;
  private final S3Service s3Service;
  private final PasswordEncoder passwordEncoder;
  private final EmailValidator emailValidator;

  public DashBoardService(UserRepository userRepository, S3Service s3Service, PasswordEncoder passwordEncoder,
                          EmailValidator emailValidator) {
    this.userRepository = userRepository;
    this.s3Service = s3Service;
    this.passwordEncoder = passwordEncoder;
    this.emailValidator = emailValidator;
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

      User user = optionalUser.get();

      String presignedUrl = null;
      if (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
        try {
          presignedUrl = s3Service.generatePresignedDownloadUrl(user.getAvatarUrl(), Duration.ofHours(2));
        } catch (Exception ignored) {}
      }

      Map<String, Object> data = new HashMap<>();
      data.put("username", user.getUsername());
      data.put("profileImage", presignedUrl);

      String phone = user.getPhoneNumber();
      if (phone != null && !phone.isBlank()) {
        data.put("phoneNumber", phone);
      } else {
        data.put("phoneNumber", null);
      }

      return new ApiResponse<>(200, "User profile fetched successfully", data);

    } catch (Exception e) {
      return new ApiResponse<>(500, "An unexpected error occurred: " + e.getMessage(), null);
    }
  }

  public ApiResponse<Map<String, Object>> saveProfileChanges(
    String email,
    String newEmail,
    String oldPassword,
    String newPassword,
    String newUsername,
    MultipartFile image
  ) {
    if (email == null || email.isBlank()) {
      return ApiResponse.error(400, "Email (identifier) is required");
    }

    try {
      User user = userRepository.findByEmail(email.trim().toLowerCase())
        .orElseThrow(() -> new RuntimeException("User not found"));

      Map<String, Object> result = new HashMap<>();

      List<Function<Void, ApiResponse<Map<String, Object>>>> steps = List.of(
        unused -> processEmailUpdate(user, newEmail, result),
        unused -> processPasswordUpdate(user, oldPassword, newPassword, result),
        unused -> processUsernameUpdate(user, newUsername, result),
        unused -> processImageUpload(user, image, result)
      );

      for (Function<Void, ApiResponse<Map<String, Object>>> step : steps) {
        ApiResponse<Map<String, Object>> error = step.apply(null);
        if (error != null) {
          return error;
        }
      }

      userRepository.save(user);
      addPresignedPreviewIfMissing(user, result);

      return ApiResponse.success(200, "Profile updated successfully", result);

    } catch (RuntimeException e) {
      return ApiResponse.error(400, e.getMessage());
    } catch (Exception e) {
      return ApiResponse.error(500, "Unexpected error: " + e.getMessage());
    }
  }

  private void addPresignedPreviewIfMissing(User user, Map<String, Object> result) {
    if (!result.containsKey("profileImage") || result.get("profileImage") != null) {
      return;
    }

    try {
      String previewUrl = s3Service.generatePresignedDownloadUrl(
        user.getAvatarUrl(),
        Duration.ofHours(2)
      );
      result.put("profileImage", previewUrl);
    } catch (Exception ignored) {}
  }

  private ApiResponse<Map<String, Object>> processEmailUpdate(User user, String newEmail,
                                                              Map<String, Object> result) {
    if (newEmail == null || newEmail.isBlank()) {
      return null;
    }

    String normalizedNewEmail = newEmail.trim().toLowerCase();

    if (normalizedNewEmail.equalsIgnoreCase(user.getEmail())) {
      return null;
    }

    if (!emailValidator.isEmail(normalizedNewEmail)) {
      return new ApiResponse<>(400, "Invalid new email format", null);
    }

    if (userRepository.existsByEmail(normalizedNewEmail)) {
      return new ApiResponse<>(409, "New email already in use", null);
    }

    user.setEmail(normalizedNewEmail);
    result.put("email", normalizedNewEmail);
    return null;
  }

  private ApiResponse<Map<String, Object>> processPasswordUpdate(User user, String oldPassword, String newPassword,
                                                                 Map<String, Object> result) {
    boolean wantsPasswordChange = (oldPassword != null && !oldPassword.isBlank())
      || (newPassword != null && !newPassword.isBlank());

    if (!wantsPasswordChange) {
      return null;
    }

    if (oldPassword == null || oldPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
      return new ApiResponse<>(400,
        "Both oldPassword and newPassword are required to change password", null);
    }

    if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
      return new ApiResponse<>(401, "Old password is incorrect", null);
    }

    user.setPassword(passwordEncoder.encode(newPassword));
    int pv = Objects.requireNonNullElse(user.getPasswordVersion(), 0);
    user.setPasswordVersion(pv + 1);
    result.put("passwordChanged", true);
    return null;
  }

  private ApiResponse<Map<String, Object>> processUsernameUpdate(User user, String newUsername, Map<String, Object> result) {
    if (newUsername == null || newUsername.isBlank()) {
      return null;
    }

    if (user.getUsername() != null && user.getUsername().equals(newUsername)) {
      return null;
    }

    if (!newUsername.matches("^[a-zA-Z0-9_.-]{3,20}$")) {
      return new ApiResponse<>(400,
        "Username must be 3–20 characters and may include letters, numbers, '.', '-', '_'", null);
    }

    if (isUsernameTakenByAnotherUser(newUsername, user)) {
      return new ApiResponse<>(409, "Username already taken. Please choose another.", null);
    }

    user.setUsername(newUsername);
    result.put("username", newUsername);
    return null;
  }

  private ApiResponse<Map<String, Object>> processImageUpload(User user, MultipartFile image,
                                                              Map<String, Object> result) {
    if (image == null || image.isEmpty()) {
      return null;
    }

    try {
      ImageValidator.validate(image);
    } catch (RuntimeException e) {
      return new ApiResponse<>(400, e.getMessage(), null);
    }

    String key = buildS3Key(user.getEmail(), image);
    boolean uploaded = uploadToS3(key, image);

    if (!uploaded) {
      return new ApiResponse<>(500, "Failed to upload profile image", null);
    }

    user.setAvatarUrl(key);
    String previewUrl = null;
    try {
      previewUrl = s3Service.generatePresignedDownloadUrl(key, Duration.ofHours(2));
    } catch (Exception ignored) {}

    result.put("profileImage", previewUrl);
    return null;
  }

}
