package org.spacehub.service;

import org.spacehub.DTO.LocalGroup.DeleteLocalGroupRequest;
import org.spacehub.DTO.LocalGroup.JoinLocalGroupRequest;
import org.spacehub.DTO.LocalGroup.LocalGroupMemberDTO;
import org.spacehub.DTO.LocalGroup.LocalGroupResponse;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.LocalGroup.LocalGroup;
import org.spacehub.entities.User.User;
import org.spacehub.repository.UserRepository;
import org.spacehub.repository.localgroup.LocalGroupRepository;
import org.spacehub.service.Interface.ILocalGroupService;
import org.spacehub.utils.S3UrlHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.time.Duration;
import java.util.stream.Collectors;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Transactional
@Service
public class LocalGroupService implements ILocalGroupService {

  private final LocalGroupRepository localGroupRepository;
  private final UserRepository userRepository;
  private final S3Service s3Service;
  private final S3UrlHelper s3UrlHelper;

  public static class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
      super(message);
    }
  }

  public LocalGroupService(LocalGroupRepository localGroupRepository, UserRepository userRepository,
                           S3Service s3Service, S3UrlHelper s3UrlHelper) {
    this.localGroupRepository = localGroupRepository;
    this.userRepository = userRepository;
    this.s3Service = s3Service;
    this.s3UrlHelper = s3UrlHelper;
  }

  public ResponseEntity<ApiResponse<LocalGroupResponse>> createLocalGroup(
    String name, String description, String creatorEmail, MultipartFile imageFile) {

    if (name == null || name.isBlank() || creatorEmail == null || creatorEmail.isBlank()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400,
        "name and creatorEmail are required", null));
    }

    if (imageFile == null || imageFile.isEmpty()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Group image is required",
        null));
    }

    try {
      User creator = userRepository.findByEmail(creatorEmail)
        .orElseThrow(() -> new ResourceNotFoundException("Creator not found"));

      validateImage(imageFile);

      String fileName = System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();
      String key = "local-groups/" + name.replaceAll("[^a-zA-Z0-9]", "_") + "/" + fileName;

      s3Service.uploadFile(key, imageFile.getInputStream(), imageFile.getSize());
      String imageUrl = s3Service.generatePresignedDownloadUrl(key, Duration.ofHours(2));

      LocalGroup group = new LocalGroup();
      group.setName(name.trim());
      group.setDescription(description);
      group.setCreatedBy(creator);
      group.setImageUrl(key);
      group.setCreatedAt(LocalDateTime.now());
      group.setUpdatedAt(LocalDateTime.now());
      group.getMembers().add(creator);

      UUID roomCode = UUID.randomUUID();
      ChatRoom chatRoom = ChatRoom.builder().name(name + " Chat Room")
              .roomCode(roomCode).community(null).build();

      group.setChatRoom(chatRoom);

      LocalGroup saved = localGroupRepository.save(group);

      LocalGroupResponse resp = toResponse(saved);
      resp.setImageUrl(imageUrl);

      return ResponseEntity.status(201).body(new ApiResponse<>(201,
        "Local group created successfully", resp));
    } catch (IOException e) {
      return ResponseEntity.internalServerError().body(
        new ApiResponse<>(500, "Error uploading image: " + e.getMessage(), null));
    } catch (RuntimeException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(
        new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  public ResponseEntity<ApiResponse<String>> joinLocalGroup(JoinLocalGroupRequest req) {
    if (req.getGroupId() == null || req.getUserEmail() == null || req.getUserEmail().isBlank()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400,
        "groupId and userEmail are required", null));
    }

    LocalGroup group = localGroupRepository.findById(req.getGroupId())
      .orElseThrow(() -> new ResourceNotFoundException("Local group not found"));

    User user = userRepository.findByEmail(req.getUserEmail())
      .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    boolean alreadyMember = group.getMembers().stream().anyMatch(u -> u.getId().equals(user.getId()));
    if (alreadyMember) {
      return ResponseEntity.status(403).body(new ApiResponse<>(403, "User already a member",
        null));
    }

    group.getMembers().add(user);
    localGroupRepository.save(group);

    return ResponseEntity.ok(new ApiResponse<>(200, "Joined local group successfully", null));
  }

  public ResponseEntity<ApiResponse<String>> deleteLocalGroup(DeleteLocalGroupRequest req) {
    if (req.getGroupId() == null || req.getRequesterEmail() == null || req.getRequesterEmail().isBlank()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400,
        "groupId and requesterEmail are required", null));
    }

    LocalGroup group = localGroupRepository.findById(req.getGroupId())
      .orElseThrow(() -> new ResourceNotFoundException("Local group not found"));

    User requester = userRepository.findByEmail(req.getRequesterEmail())
      .orElseThrow(() -> new ResourceNotFoundException("Requester not found"));

    if (!Objects.equals(group.getCreatedBy().getId(), requester.getId())) {
      return ResponseEntity.status(403).body(new ApiResponse<>(403,
        "Only the group creator can delete this group", null));
    }

    localGroupRepository.delete(group);
    return ResponseEntity.ok(new ApiResponse<>(200, "Local group deleted successfully",
      null));
  }

  public ResponseEntity<ApiResponse<List<LocalGroupResponse>>> listAllLocalGroups(String requesterEmail) {
    List<LocalGroup> all = localGroupRepository.findAllWithCreatorAndMembers();

    if (requesterEmail == null || requesterEmail.isBlank()) {
      List<LocalGroupResponse> out = all.stream().map(this::toResponse).collect(Collectors.toList());
      return ResponseEntity.ok(new ApiResponse<>(200, "All local groups fetched", out));
    }

    Optional<User> userOpt = userRepository.findByEmail(requesterEmail);

    if (userOpt.isEmpty()) {
      List<LocalGroupResponse> out = all.stream().map(this::toResponse).collect(Collectors.toList());
      return ResponseEntity.ok(new ApiResponse<>(200, "All local groups fetched (user not found)",
        out));
    }

    List<LocalGroupResponse> out = all.stream()
      .filter(group -> group.getMembers() != null &&
        group.getMembers().stream()
          .anyMatch(member -> member.getEmail().equalsIgnoreCase(requesterEmail)))
      .map(this::toResponse)
      .collect(Collectors.toList());


    return ResponseEntity.ok(new ApiResponse<>(200, "Filtered local groups fetched", out));
  }

  public ResponseEntity<ApiResponse<LocalGroupResponse>> getLocalGroup(UUID id) {
    LocalGroup group = localGroupRepository.findById(id)
      .orElseThrow(() -> new ResourceNotFoundException("Local group not found"));
    return ResponseEntity.ok(new ApiResponse<>(200, "Local group fetched", toResponse(group)));
  }

  private LocalGroupResponse toResponse(LocalGroup g) {
    LocalGroupResponse r = new LocalGroupResponse();
    r.setId(g.getId());
    r.setName(g.getName());
    r.setDescription(g.getDescription());
    if (g.getCreatedBy() != null) {
      r.setCreatedByEmail(g.getCreatedBy().getEmail());
    }
    r.setCreatedAt(g.getCreatedAt());
    r.setUpdatedAt(g.getUpdatedAt());
    if (g.getMembers() != null) {
      r.setTotalMembers(g.getMembers().size());
    } else {
      r.setTotalMembers(0);
    }
    if (g.getChatRoom() != null) {
      r.setChatRoomCode(String.valueOf(g.getChatRoom().getRoomCode()));
    }
    List<String> memberEmails = g.getMembers().stream().map(User::getEmail).collect(Collectors.toList());
    r.setMemberEmails(memberEmails);

    String key = g.getImageUrl();
    if (key != null && !key.isBlank()) {
      try {
        String presigned = s3Service.generatePresignedDownloadUrl(key, Duration.ofHours(1));
        r.setImageUrl(presigned);
      } catch (Exception ignored) {
        r.setImageUrl(null);
      }
      r.setImageKey(key);
    } else {
      r.setImageUrl(null);
      r.setImageKey(null);
    }
    return r;
  }


  private void validateImage(MultipartFile file) {
    if (file.isEmpty()) {
      throw new RuntimeException("File is empty");
    }

    if (file.getSize() > 2 * 1024 * 1024)
      throw new RuntimeException("File size exceeds 2 MB");

    String contentType = file.getContentType();

    if (contentType == null || !contentType.startsWith("image/"))
      throw new RuntimeException("Only image files are allowed");
  }

  public ResponseEntity<ApiResponse<Map<String, Object>>> searchLocalGroups(
    String q, String requesterEmail, int page, int size) {

    if (q == null || q.isBlank()) {
      return ResponseEntity.ok(new ApiResponse<>(200, "Empty query", Map.of(
        "groups", Collections.emptyList(),
        "page", 0, "size", 0, "totalElements", 0, "totalPages", 0)));
    }

    Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
    Page<LocalGroup> groupPage = localGroupRepository
      .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(q, q, pageable);

    User requester = null;
    if (requesterEmail != null && !requesterEmail.isBlank()) {
      requester = userRepository.findByEmail(requesterEmail).orElse(null);
    }
    final User finalRequester = requester;

    List<Map<String, Object>> results = groupPage.getContent().stream().map(g -> {
      Map<String, Object> m = new HashMap<>();
      m.put("id", g.getId());
      m.put("name", g.getName());
      m.put("description", g.getDescription());
      m.put("createdAt", g.getCreatedAt());
      m.put("updatedAt", g.getUpdatedAt());

      Map<String, Object> img = s3UrlHelper.generatePresignedUrl(g.getImageUrl(), Duration.ofHours(1));
      m.put("imageUrl", img.get("url"));
      m.put("imageKey", img.get("key"));

      if (finalRequester != null) {
        boolean isMember = g.getMembers().stream()
          .anyMatch(u -> u.getId().equals(finalRequester.getId()));
        m.put("isMember", isMember);
      }
      m.put("totalMembers", g.getMembers() == null ? 0 : g.getMembers().size());
      return m;
    }).collect(Collectors.toList());

    Map<String, Object> body = new HashMap<>();
    body.put("groups", results);
    body.put("page", groupPage.getNumber());
    body.put("size", groupPage.getSize());
    body.put("totalElements", groupPage.getTotalElements());
    body.put("totalPages", groupPage.getTotalPages());

    return ResponseEntity.ok(new ApiResponse<>(200, "Local group search results", body));
  }

  public ResponseEntity<?> enterOrJoinLocalGroup(UUID groupId, String requesterEmail) {
    if (requesterEmail == null || requesterEmail.isBlank()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "requesterEmail is required",
        null));
    }

    LocalGroup group = localGroupRepository.findById(groupId)
      .orElseThrow(() -> new ResourceNotFoundException("Local group not found"));

    User user = userRepository.findByEmail(requesterEmail)
      .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    boolean isMember = group.getMembers().stream().anyMatch(u -> u.getId().equals(user.getId()));

    if (isMember) {
      LocalGroupResponse resp = toResponse(group);
      String key = group.getImageUrl();
      if (key != null && !key.isBlank()) {
        try {
          resp.setImageUrl(s3Service.generatePresignedDownloadUrl(key, Duration.ofHours(1)));
        } catch (Exception ignored) { }
      }
      return ResponseEntity.ok(new ApiResponse<>(200, "Local group fetched", resp));
    } else {
      boolean alreadyMember = group.getMembers().stream().anyMatch(u -> u.getId().equals(user.getId()));
      if (!alreadyMember) {
        group.getMembers().add(user);
        localGroupRepository.save(group);
      }
      return ResponseEntity.ok(new ApiResponse<>(200, "Joined local group", Map.of("joined",
        true)));
    }
  }

  public ResponseEntity<ApiResponse<List<LocalGroupMemberDTO>>> getLocalGroupMembers(UUID groupId) {
    if (groupId == null) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "groupId is required",
        null));
    }

    Optional<LocalGroup> opt = localGroupRepository.findById(groupId);
    if (opt.isEmpty()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Local group not found",
        null));
    }

    LocalGroup group = opt.get();
    Set<User> members = group.getMembers();
    if (members == null) {
      members = Collections.emptySet();
    }

    List<LocalGroupMemberDTO> result = members.stream().map(user -> {
      LocalGroupMemberDTO dto = new LocalGroupMemberDTO();
      dto.setId(UUID.fromString(String.valueOf(user.getId())));
      dto.setUsername(user.getUsername());
      dto.setEmail(user.getEmail());
      dto.setAvatarKey(user.getAvatarUrl());
      if (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
        try {
          dto.setAvatarPreviewUrl(s3Service.generatePresignedDownloadUrl(user.getAvatarUrl(),
            Duration.ofMinutes(60)));
        } catch (Exception ignored) {
          dto.setAvatarPreviewUrl(null);
        }
      } else {
        dto.setAvatarPreviewUrl(null);
      }
      return dto;
    }).collect(Collectors.toList());

    return ResponseEntity.ok(new ApiResponse<>(200, "Local group members fetched", result));
  }

  @Override
  public ResponseEntity<ApiResponse<LocalGroupResponse>> updateLocalGroupSettings(
    UUID groupId, String requesterEmail, MultipartFile imageFile, String newName) {

    if (isInvalidRequest(groupId, requesterEmail)) {
      return badRequest("groupId and requesterEmail are required");
    }

    try {
      LocalGroup group = findGroupOrThrow(groupId);
      User requester = findUserOrThrow(requesterEmail);

      if (!isGroupCreator(group, requester)) {
        return forbidden();
      }

      boolean changed = updateNameIfNeeded(group, newName);
      changed |= updateImageIfNeeded(group, imageFile);

      return buildResponse(group, changed);

    } catch (IOException ioe) {
      return serverError("Error uploading image: " + ioe.getMessage());
    } catch (ResourceNotFoundException rnfe) {
      return badRequest(rnfe.getMessage());
    } catch (Exception e) {
      return serverError("Unexpected error: " + e.getMessage());
    }
  }

  private boolean isInvalidRequest(UUID groupId, String requesterEmail) {
    return groupId == null || requesterEmail == null || requesterEmail.isBlank();
  }

  private ResponseEntity<ApiResponse<LocalGroupResponse>> badRequest(String message) {
    return ResponseEntity.badRequest().body(new ApiResponse<>(400, message, null));
  }

  private ResponseEntity<ApiResponse<LocalGroupResponse>> forbidden() {
    return ResponseEntity.status(403).body(new ApiResponse<>(403,
      "Only the group creator can update settings", null));
  }

  private ResponseEntity<ApiResponse<LocalGroupResponse>> serverError(String message) {
    return ResponseEntity.internalServerError().body(new ApiResponse<>(500, message, null));
  }

  private LocalGroup findGroupOrThrow(UUID groupId) {
    return localGroupRepository.findById(groupId)
      .orElseThrow(() -> new ResourceNotFoundException("Local group not found"));
  }

  private User findUserOrThrow(String requesterEmail) {
    return userRepository.findByEmail(requesterEmail)
      .orElseThrow(() -> new ResourceNotFoundException("Requester not found"));
  }

  private boolean isGroupCreator(LocalGroup group, User requester) {
    return Objects.equals(group.getCreatedBy().getId(), requester.getId());
  }

  private boolean updateNameIfNeeded(LocalGroup group, String newName) {
    if (newName == null || newName.isBlank()) {
      return false;
    }

    String normalized = newName.trim();
    if (normalized.equals(group.getName())) {
      return false;
    }

    group.setName(normalized);
    if (group.getChatRoom() != null) {
      group.getChatRoom().setName(normalized + " Room");
    }
    return true;
  }

  private boolean updateImageIfNeeded(LocalGroup group, MultipartFile imageFile) throws IOException {
    if (imageFile == null || imageFile.isEmpty()) {
      return false;
    }

    validateImage(imageFile);

    String safeName = Optional.ofNullable(group.getName())
      .filter(name -> !name.isBlank())
      .orElse(group.getId().toString())
      .replaceAll("\\W", "_");

    String fileName = System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();
    String key = "local-groups/" + safeName + "/" + fileName;

    s3Service.uploadFile(key, imageFile.getInputStream(), imageFile.getSize());
    group.setImageUrl(key);
    return true;
  }

  private ResponseEntity<ApiResponse<LocalGroupResponse>> buildResponse(LocalGroup group, boolean changed) {
    if (changed) {
      group.setUpdatedAt(LocalDateTime.now());
      LocalGroup saved = localGroupRepository.save(group);

      LocalGroupResponse resp = toResponse(saved);
      setPresignedUrl(resp, saved.getImageUrl());

      return ResponseEntity.ok(new ApiResponse<>(200, "Local group settings updated", resp));
    }
    return ResponseEntity.ok(new ApiResponse<>(200, "No changes applied", toResponse(group)));
  }

  private void setPresignedUrl(LocalGroupResponse resp, String key) {
    if (key != null && !key.isBlank()) {
      try {
        resp.setImageUrl(s3Service.generatePresignedDownloadUrl(key, Duration.ofHours(1)));
      } catch (Exception ignored) {}
    }
  }

}
