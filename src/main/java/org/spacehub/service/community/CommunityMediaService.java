package org.spacehub.service.community;

import lombok.RequiredArgsConstructor;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.User.User;
import org.spacehub.repository.User.UserRepository;
import org.spacehub.repository.community.CommunityRepository;
import org.spacehub.repository.community.CommunityUserRepository;
import org.spacehub.service.File.S3Service;
import org.spacehub.utils.ImageValidator;
import org.spacehub.utils.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class CommunityMediaService {

  private final CommunityRepository communityRepository;
  private final UserRepository userRepository;
  private final CommunityUserRepository communityUserRepository;
  private final S3Service s3Service;

  public ResponseEntity<?> uploadCommunityAvatar(UUID communityId, MultipartFile imageFile) {
    String requesterEmail = SecurityUtils.getCurrentUserEmail();
    if (requesterEmail == null || requesterEmail.isBlank()) {
      return badRequest("requesterEmail is required");
    }

    if (imageFile == null || imageFile.isEmpty()) {
      return badRequest("Image file is required");
    }

    try {
      Community community = getCommunityOrThrow(communityId);
      User requester = getUserOrThrow(requesterEmail);

      if (!isUserAdminInCommunity(community, requester)) {
        return forbidden();
      }

      ImageValidator.validate(imageFile);

      String fileName = System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();
      String key = "communities/" + community.getName().replaceAll("[^a-zA-Z0-9]", "_") +
        "/avatar/" + fileName;

      s3Service.uploadFile(key, imageFile.getInputStream(), imageFile.getSize());
      community.setImageUrl(key);
      communityRepository.save(community);

      String presigned = s3Service.generatePresignedDownloadUrl(key, Duration.ofHours(2));
      return ok(Map.of("presignedUrl", presigned, "key", key));
    } catch (IOException e) {
      return serverError("Error uploading image: " + e.getMessage());
    } catch (RuntimeException e) {
      return badRequest(e.getMessage());
    }
  }

  public ResponseEntity<?> uploadCommunityBanner(
    UUID communityId,
    MultipartFile bannerFile,
    MultipartFile communityAvatarFile,
    MultipartFile userAvatarFile,
    String newName,
    String newDescription
  ) {
    String requesterEmail = SecurityUtils.getCurrentUserEmail();
    if (requesterEmail == null || requesterEmail.isBlank()) {
      return badRequest("requesterEmail is required");
    }

    try {
      Community community = getCommunityOrThrow(communityId);
      User requester = getUserOrThrow(requesterEmail);

      if (!isUserAdminInCommunity(community, requester)) {
        return forbidden();
      }

      Map<String, Object> body = new HashMap<>();
      processNameAndDescription(community, newName, newDescription, body);

      handleImageUploads(community, requester, bannerFile, communityAvatarFile, userAvatarFile, body);

      communityRepository.save(community);
      if (community.getCreatedBy() != null) {
        body.put("createdBy", community.getCreatedBy().getEmail());
      }
      body.put("createdAt", community.getCreatedAt());

      return ok(body);
    } catch (RuntimeException re) {
      return badRequest(re.getMessage());
    } catch (IOException ioe) {
      return serverError("Error uploading image: " + ioe.getMessage());
    } catch (Exception e) {
      return serverError("Unexpected error: " + e.getMessage());
    }
  }

  public String uploadImage(String name, MultipartFile imageFile) throws IOException {
    ImageValidator.validate(imageFile);
    String fileName = System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();
    String key = "communities/" + safeName(name) + "/" + fileName;
    s3Service.uploadFile(key, imageFile.getInputStream(), imageFile.getSize());
    return key;
  }

  public String generatePresignedSafely(String key) {
    if (key == null || key.isBlank()) {
      return null;
    }
    if (key.startsWith("http://") || key.startsWith("https://")) {
      return key;
    }
    try {
      return s3Service.generatePresignedDownloadUrl(key, Duration.ofHours(2));
    } catch (Exception ignored) {
      return null;
    }
  }

  private void handleImageUploads(
    Community community,
    User requester,
    MultipartFile bannerFile,
    MultipartFile communityAvatarFile,
    MultipartFile userAvatarFile,
    Map<String, Object> body
  ) throws IOException {
    String safeCommunityName = safeName(community.getName());

    uploadCommunityAvatarFile(community, communityAvatarFile, safeCommunityName, body);
    uploadCommunityBannerFile(community, bannerFile, safeCommunityName, body);
    uploadUserAvatarFile(requester, userAvatarFile, body);
  }

  private void uploadCommunityAvatarFile(Community community, MultipartFile file, String safeName,
                                         Map<String, Object> body) throws IOException {
    if (file == null || file.isEmpty()) {
      return;
    }

    String avatarKey = uploadAndReturnKey(file, "communities/" + safeName + "/avatar/");
    community.setImageUrl(avatarKey);
    body.put("communityAvatarKey", avatarKey);
    body.put("communityAvatarUrl", tryGeneratePresigned(avatarKey));
  }

  private void uploadCommunityBannerFile(Community community, MultipartFile file, String safeName,
                                         Map<String, Object> body) throws IOException {
    if (file == null || file.isEmpty()) {
      return;
    }

    String bannerKey = uploadAndReturnKey(file, "communities/" + safeName + "/banner/");
    community.setImageUrl(bannerKey);
    body.put("bannerKey", bannerKey);
    body.put("bannerUrl", tryGeneratePresigned(bannerKey));
  }

  private void uploadUserAvatarFile(User requester, MultipartFile file, Map<String, Object> body) throws IOException {
    if (file == null || file.isEmpty()) {
      return;
    }

    String userKey = uploadAndReturnKey(file, "users/" + requester.getId() + "/avatar/");
    requester.setAvatarUrl(userKey);
    userRepository.save(requester);
    body.put("userAvatarKey", userKey);
    body.put("userAvatarUrl", tryGeneratePresigned(userKey));
  }

  private String uploadAndReturnKey(MultipartFile file, String prefix) throws IOException {
    ImageValidator.validate(file);
    String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
    String key = prefix + fileName;
    s3Service.uploadFile(key, file.getInputStream(), file.getSize());
    return key;
  }

  private String tryGeneratePresigned(String key) {
    if (key == null || key.isBlank()) {
      return null;
    }
    if (key.startsWith("http://") || key.startsWith("https://")) {
      return key;
    }
    try {
      return s3Service.generatePresignedDownloadUrl(key, Duration.ofHours(2));
    } catch (Exception ignored) {
      return null;
    }
  }

  private void processNameAndDescription(Community community, String newName, String newDescription,
                                         Map<String, Object> body) {
    if (newName != null && !newName.isBlank()) {
      String normalized = newName.trim();
      if (!normalized.equalsIgnoreCase(community.getName())) {
        if (communityRepository.existsByNameIgnoreCase(normalized)) {
          throw new RuntimeException("Community name already in use");
        }
        community.setName(normalized);
        body.put("name", normalized);
      }
    }
    if (newDescription != null) {
      community.setDescription(newDescription);
      body.put("description", newDescription);
    }
  }

  private boolean isUserAdminInCommunity(Community community, User requester) {
    if (community.getCreatedBy() != null && community.getCreatedBy().getId().equals(requester.getId())) {
      return true;
    }
    return communityUserRepository.findByCommunityAndUser(community, requester)
      .map(cu -> cu.getRole() != null && cu.getRole().name().equalsIgnoreCase("ADMIN"))
      .orElse(false);
  }

  private String safeName(String name) {
    if (name == null) {
      return "community";
    }
    return name.replaceAll("[^a-zA-Z0-9]", "_");
  }

  private Community getCommunityOrThrow(UUID communityId) {
    return communityRepository.findById(communityId)
      .orElseThrow(() -> new RuntimeException("Community not found"));
  }

  private User getUserOrThrow(String email) {
    return userRepository.findByEmail(email)
      .orElseThrow(() -> new RuntimeException("User not found"));
  }

  private ResponseEntity<ApiResponse<Object>> ok(Object data) {
    return ResponseEntity.ok(new ApiResponse<>(200, "Banner applied successfully", data));
  }

  private ResponseEntity<ApiResponse<Object>> badRequest(String message) {
    return ResponseEntity.badRequest().body(new ApiResponse<>(400, message, null));
  }

  private ResponseEntity<ApiResponse<Object>> forbidden() {
    return ResponseEntity.status(403).body(new ApiResponse<>(403,
      "Only community admin can change banner/info", null));
  }

  private ResponseEntity<ApiResponse<Object>> serverError(String message) {
    return ResponseEntity.internalServerError().body(new ApiResponse<>(500, message, null));
  }
}
