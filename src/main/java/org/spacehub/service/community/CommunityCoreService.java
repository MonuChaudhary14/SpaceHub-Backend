package org.spacehub.service.community;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.Community.DeleteCommunityDTO;
import org.spacehub.DTO.Community.UpdateCommunityDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.Community.CommunityUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.entities.User.User;
import org.spacehub.repository.ChatRoom.ChatRoomRepository;
import org.spacehub.repository.Notification.NotificationRepository;
import org.spacehub.repository.User.UserRepository;
import org.spacehub.repository.community.CommunityRepository;
import org.spacehub.repository.community.CommunityUserRepository;
import org.spacehub.service.File.S3Service;
import org.spacehub.utils.ImageValidator;
import org.spacehub.utils.S3UrlHelper;
import org.spacehub.utils.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class CommunityCoreService {

  private final CommunityRepository communityRepository;
  private final UserRepository userRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final CommunityUserRepository communityUserRepository;
  private final NotificationRepository notificationRepository;
  private final S3Service s3Service;
  private final S3UrlHelper s3UrlHelper;
  private final CommunityMediaService communityMediaService;

  public ResponseEntity<ApiResponse<Map<String, Object>>> createCommunity(
    String name, String description, MultipartFile imageFile) {
    String createdByEmail = SecurityUtils.getCurrentUserEmail();

    try {
      validateCommunityInputs(name, description, createdByEmail, imageFile);

      User creator = getCreator(createdByEmail);
      ImageValidator.validate(imageFile);

      String imageKey = communityMediaService.uploadImage(name, imageFile);
      String imageUrl = communityMediaService.generatePresignedSafely(imageKey);

      Community savedCommunity = saveCommunity(name, description, creator, imageKey);
      addAdminUser(savedCommunity, creator);

      Map<String, Object> responseData = new HashMap<>();
      responseData.put("communityId", savedCommunity.getId());
      responseData.put("name", savedCommunity.getName());
      responseData.put("imageUrl", imageUrl);

      return ResponseEntity.status(201)
        .body(new ApiResponse<>(201, "Community created successfully", responseData));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    } catch (IOException e) {
      return ResponseEntity.internalServerError()
        .body(new ApiResponse<>(500, "Error uploading image: " + e.getMessage(), null));
    } catch (Exception e) {
      return ResponseEntity.internalServerError()
        .body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  public ResponseEntity<ApiResponse<Void>> deleteCommunityByName(DeleteCommunityDTO deleteCommunity) {
    try {
      if (deleteCommunity == null || deleteCommunity.getName() == null || deleteCommunity.getName().trim().isEmpty()) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community name cannot be empty", null));
      }

      Community community = communityRepository.findByNameWithUsers(deleteCommunity.getName())
        .orElseThrow(() -> new IllegalArgumentException("Community not found"));
      User user = userRepository.findByEmail(SecurityUtils.getCurrentUserEmail())
        .orElseThrow(() -> new IllegalArgumentException("User not found"));

      if (!community.getCreatedBy().getId().equals(user.getId())) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403, "You are not authorized to delete this community", null));
      }

      clearCommunityRelations(community);

      communityRepository.save(community);
      notificationRepository.deleteByCommunityId(community.getId());
      communityRepository.delete(community);

      return ResponseEntity.ok(new ApiResponse<>(200, "Community deleted successfully", null));

    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, e.getMessage(), null));
    }
  }

  public ResponseEntity<ApiResponse<Community>> updateCommunityInfo(UpdateCommunityDTO dto) {
    if (dto == null || dto.getCommunityId() == null) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Invalid request: Missing required fields", null));
    }

    Community community = communityRepository.findById(dto.getCommunityId()).orElse(null);
    if (community == null) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));
    }

    User requester = userRepository.findByEmail(SecurityUtils.getCurrentUserEmail()).orElse(null);
    if (requester == null) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Requester not found", null));
    }

    boolean isMember = isUserMemberOfCommunity(requester, community);
    if (!isMember) {
      return ResponseEntity.status(403).body(new ApiResponse<>(403, "You are not a member of this community", null));
    }

    if (!canUpdateCommunity(community, requester)) {
      return ResponseEntity.status(403).body(new ApiResponse<>(403, "Only admins or workspace owners can update community info", null));
    }

    if (dto.getName() != null && !dto.getName().isBlank()) {
      community.setName(dto.getName());
    }
    if (dto.getDescription() != null && !dto.getDescription().isBlank()) {
      community.setDescription(dto.getDescription());
    }
    communityRepository.save(community);

    return ResponseEntity.ok(new ApiResponse<>(200, "Community info updated successfully", community));
  }

  public ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> listAllCommunities() {
    List<Community> all = communityRepository.findAll();
    List<Map<String, Object>> out = all.stream().map(this::buildCommunityBasicInfo).toList();
    return ResponseEntity.ok(new ApiResponse<>(200, "Communities fetched successfully", Map.of("communities", out)));
  }

  public ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> listMyCommunities() {
    try {
      String requesterEmail = SecurityUtils.getCurrentUserEmail();
      if (requesterEmail == null || requesterEmail.isBlank()) {
        return ResponseEntity.status(401).body(new ApiResponse<>(401, "Unauthorized: valid access token required", null));
      }

      String normalizedEmail = requesterEmail.trim().toLowerCase();
      Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
      if (userOpt.isEmpty()) {
        return ResponseEntity.ok(new ApiResponse<>(200, "User not found, no communities fetched", Map.of("communities", List.of())));
      }

      List<Map<String, Object>> userCommunities = buildCommunityListForUser(normalizedEmail);
      for (Map<String, Object> community : userCommunities) {
        UUID communityId = (UUID) community.get("communityId");
        Community c = communityRepository.findByIdWithUsers(communityId).orElse(null);
        long memberCount = 0;
        if (c != null) {
          memberCount = c.getCommunityUsers().stream()
            .filter(cu -> cu.getRole() != null)
            .filter(cu -> !cu.isBlocked() && !cu.isBanned())
            .map(CommunityUser::getUser)
            .filter(Objects::nonNull)
            .map(User::getId)
            .distinct()
            .count();
        }
        community.put("memberCount", memberCount);
      }

      return ResponseEntity.ok(new ApiResponse<>(200, "User's communities fetched with member counts", Map.of("communities", userCommunities)));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  public ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityDetailsWithAdminFlag(UUID communityId) {
    try {
      String requesterEmail = SecurityUtils.getCurrentUserEmail();
      if (communityId == null || requesterEmail == null || requesterEmail.isBlank()) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community ID and requester email are required", null));
      }

      Optional<Community> optionalCommunity = communityRepository.findById(communityId);
      if (optionalCommunity.isEmpty()) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));
      }

      Community community = optionalCommunity.get();
      Optional<User> optionalUser = userRepository.findByEmail(requesterEmail.trim().toLowerCase());
      if (optionalUser.isEmpty()) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Requester not found", null));
      }
      User requester = optionalUser.get();

      Optional<CommunityUser> cuOpt = community.getCommunityUsers().stream()
        .filter(cu -> cu.getUser().getId().equals(requester.getId()))
        .findFirst();

      if (cuOpt.isEmpty()) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403, "You are not a member of this community", null));
      }

      CommunityUser communityUser = cuOpt.get();
      if (communityUser.isBanned()) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403, "You are banned from this community", null));
      }

      Role role = communityUser.getRole();
      boolean isCreator = community.getCreatedBy() != null && community.getCreatedBy().getId().equals(requester.getId());
      boolean isOwner = role == Role.OWNER || isCreator;
      boolean isAdmin = role == Role.ADMIN;
      boolean isModerator = role == Role.MODERATOR;

      List<ChatRoom> rooms = chatRoomRepository.findByCommunityId(communityId);
      Map<String, Object> response = new HashMap<>();
      response.put("communityId", community.getId());
      response.put("communityName", community.getName());
      response.put("description", community.getDescription());
      response.put("rooms", rooms);

      List<Map<String, Object>> members = new ArrayList<>();
      for (CommunityUser cu : community.getCommunityUsers()) {
        Map<String, Object> memberData = new HashMap<>();
        User u = cu.getUser();
        memberData.put("email", u != null ? u.getEmail() : null);
        memberData.put("username", u != null ? u.getUsername() : null);
        memberData.put("role", cu.getRole() != null ? cu.getRole().toString() : "MEMBER");
        members.add(memberData);
      }
      response.put("members", members);
      response.put("role", role.toString());
      response.put("isOwner", isOwner);
      response.put("isAdmin", isAdmin);
      response.put("isModerator", isModerator);
      response.put("isCreator", isCreator);

      return ResponseEntity.ok(new ApiResponse<>(200, "Community details fetched successfully", response));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  public ResponseEntity<?> searchCommunities(String q, int page, int size) {
    if (q == null || q.isBlank()) {
      return listAllCommunities();
    }

    Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
    String pattern = Arrays.stream(q.split("")).filter(ch -> !ch.isBlank()).collect(Collectors.joining("%"));
    Page<Community> communityPage = communityRepository.searchByNamePattern(pattern, pageable);

    User requester = null;
    String requesterEmail = SecurityUtils.getCurrentUserEmail();
    if (requesterEmail != null && !requesterEmail.isBlank()) {
      requester = userRepository.findByEmail(requesterEmail).orElse(null);
    }
    final User finalRequester = requester;

    List<Map<String, Object>> results = communityPage.getContent().stream().map(c -> {
      Map<String, Object> m = buildCommunityBasicInfo(c);
      if (finalRequester != null) {
        boolean isMember = c.getMembers().stream().anyMatch(u -> u.getId().equals(finalRequester.getId())) ||
          c.getCommunityUsers().stream().anyMatch(cu -> cu.getUser().getId().equals(finalRequester.getId()));
        boolean isRequested = c.getPendingRequests().stream().anyMatch(u -> u.getId().equals(finalRequester.getId()));
        boolean isBlocked = c.getCommunityUsers().stream().anyMatch(cu -> cu.getUser().getId().equals(finalRequester.getId()) && cu.isBlocked());

        m.put("isMember", isMember);
        m.put("isRequested", isRequested);
        m.put("isBlocked", isBlocked);
      }
      return m;
    }).collect(Collectors.toList());

    Map<String, Object> body = buildPagedResponse(communityPage, results);
    return ResponseEntity.ok(new ApiResponse<>(200, "Search results", body));
  }

  public ResponseEntity<ApiResponse<Map<String, Object>>> discoverCommunities(int page, int size) {
    try {
      String currentUserEmail = SecurityUtils.getCurrentUserEmail();
      Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
      Page<Community> communityPage = communityRepository.findAll(pageable);
      User currentUser = userRepository.findByEmail(currentUserEmail).orElse(null);

      List<Map<String, Object>> communities = communityPage.getContent().stream()
        .map(c -> buildCommunityDiscoverDTO(c, currentUser))
        .collect(Collectors.toList());

      return ResponseEntity.ok(new ApiResponse<>(200, "Discover communities fetched successfully", buildPagedResponse(communityPage, communities)));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  public ResponseEntity<?> checkCommunityNameExists(String name) {
    if (name == null || name.isBlank()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "name is required", null));
    }
    boolean exists = communityRepository.existsByNameIgnoreCase(name.trim());
    return ResponseEntity.ok(new ApiResponse<>(200, "Check completed", Map.of("exists", exists)));
  }

  public Map<String, Object> buildCommunityBasicInfo(Community c) {
    Map<String, Object> m = new HashMap<>();
    m.put("communityId", c.getId());
    m.put("name", c.getName());
    m.put("description", c.getDescription());

    String mainKey = c.getImageUrl() != null && !c.getImageUrl().isBlank() ? c.getImageUrl() : c.getAvatarUrl();
    if (mainKey == null || mainKey.isBlank()) {
      mainKey = c.getBannerUrl();
    }

    try {
      Map<String, Object> img = s3UrlHelper.generatePresignedUrl(mainKey, Duration.ofHours(1));
      m.put("imageUrl", img.get("url"));
      m.put("imageKey", img.get("key"));
    } catch (Exception e) {
      m.put("imageUrl", null);
      m.put("imageKey", null);
    }

    if (c.getCreatedBy() != null) {
      m.put("createdBy", c.getCreatedBy().getUsername());
    }

    if (c.getCommunityUsers() != null) {
      long memberCount = c.getCommunityUsers().stream().filter(u -> !u.isBanned()).count();
      m.put("totalMembers", memberCount);
    } else {
      m.put("totalMembers", 0);
    }

    return m;
  }

  public Map<String, Object> buildCommunityDiscoverDTO(Community c, User currentUser) {
    Map<String, Object> m = new HashMap<>();
    m.put("communityId", c.getId());
    m.put("name", c.getName());
    m.put("description", c.getDescription());

    String mainKey = c.getImageUrl() != null && !c.getImageUrl().isBlank() ? c.getImageUrl() : c.getAvatarUrl();
    if (mainKey == null || mainKey.isBlank()) {
      mainKey = c.getBannerUrl();
    }

    String resolvedImg = communityMediaService.generatePresignedSafely(mainKey);

    m.put("imageUrl", resolvedImg);
    m.put("imageKey", mainKey);
    m.put("createdBy", c.getCreatedBy() != null ? c.getCreatedBy().getEmail() : null);
    m.put("createdAt", c.getCreatedAt());

    if (currentUser != null) {
      Optional<CommunityUser> membership = communityUserRepository.findByCommunityIdAndUserId(c.getId(), currentUser.getId());
      if (membership.isPresent()) {
        CommunityUser cu = membership.get();
        m.put("joined", true);
        m.put("role", cu.getRole() != null ? cu.getRole().name() : null);
        m.put("isBanned", cu.isBanned());
      } else {
        m.put("joined", false);
        m.put("role", null);
        m.put("isBanned", false);
      }
    } else {
      m.put("joined", false);
      m.put("role", null);
      m.put("isBanned", false);
    }

    List<Role> rolesToCount = List.of(Role.MEMBER, Role.MODERATOR, Role.ADMIN, Role.OWNER);
    long memberCount = communityUserRepository.countByCommunityIdAndRoleInAndIsBannedFalseAndIsBlockedFalse(c.getId(), rolesToCount);
    m.put("memberCount", memberCount);

    return m;
  }

  private void validateCommunityInputs(String name, String description, String email, MultipartFile imageFile) {
    if (name == null || name.isBlank() || description == null || description.isBlank()) {
      throw new IllegalArgumentException("All fields (name, description) are required");
    }
    if (communityRepository.existsByNameIgnoreCase(name.trim())) {
      throw new IllegalArgumentException("Community with this name already exists");
    }
    if (imageFile == null || imageFile.isEmpty()) {
      throw new IllegalArgumentException("Community image is required");
    }
  }

  private User getCreator(String email) {
    return userRepository.findByEmail(email)
      .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
  }

  private Community saveCommunity(String name, String description, User creator, String imageKey) {
    Community community = new Community();
    community.setName(name);
    community.setDescription(description);
    community.setCreatedBy(creator);
    community.setImageUrl(imageKey);
    community.setCreatedAt(LocalDateTime.now());
    return communityRepository.save(community);
  }

  private void addAdminUser(Community community, User creator) {
    if (community.getMembers() == null) {
      community.setMembers(new HashSet<>());
    }
    community.getMembers().add(creator);
    communityRepository.save(community);

    CommunityUser admin = new CommunityUser();
    admin.setCommunity(community);
    admin.setUser(creator);
    admin.setRole(Role.OWNER);
    admin.setJoinDate(LocalDateTime.now());
    admin.setBlocked(false);
    admin.setBanned(false);

    communityUserRepository.save(admin);

    if (community.getCommunityUsers() == null) {
      community.setCommunityUsers(new HashSet<>());
    }
    community.getCommunityUsers().add(admin);
    communityRepository.save(community);
  }

  private void clearCommunityRelations(Community community) {
    if (community.getCommunityUsers() != null) {
      community.getCommunityUsers().forEach(cu -> cu.setCommunity(null));
      community.getCommunityUsers().clear();
      communityUserRepository.deleteByCommunityId(community.getId());
    }
    if (community.getPendingRequests() != null) {
      community.getPendingRequests().clear();
    }
    if (community.getChatRooms() != null) {
      community.getChatRooms().forEach(room -> room.setCommunity(null));
      community.getChatRooms().clear();
    }
    if (community.getMembers() != null && !community.getMembers().isEmpty()) {
      community.getMembers().clear();
    }
  }

  private boolean isUserMemberOfCommunity(User user, Community community) {
    if (user == null || community == null) {
      return false;
    }
    if (community.getCreatedBy() != null && community.getCreatedBy().getId().equals(user.getId())) {
      return true;
    }
    if (community.getCommunityUsers() == null) {
      return false;
    }
    return community.getCommunityUsers().stream()
      .anyMatch(cu -> cu.getUser() != null && cu.getUser().getId().equals(user.getId()) && !cu.isBanned() && !cu.isBlocked());
  }

  private boolean canUpdateCommunity(Community community, User requester) {
    Role requesterRole = getUserRoleInCommunity(community, requester);
    return community.getCreatedBy().getId().equals(requester.getId()) ||
      requesterRole == Role.OWNER ||
      requesterRole == Role.ADMIN;
  }

  private Role getUserRoleInCommunity(Community community, User user) {
    return community.getCommunityUsers().stream()
      .filter(cu -> cu.getUser().getId().equals(user.getId()))
      .map(CommunityUser::getRole)
      .findFirst()
      .orElse(Role.MEMBER);
  }

  private List<Map<String, Object>> buildCommunityListForUser(String normalizedEmail) {
    Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
    if (userOpt.isEmpty()) {
      return Collections.emptyList();
    }

    User user = userOpt.get();
    List<Community> all = communityRepository.findAll();
    List<Map<String, Object>> out = new ArrayList<>();

    for (Community c : all) {
      boolean isCreator = c.getCreatedBy() != null && c.getCreatedBy().getId().equals(user.getId());
      boolean isMember = communityUserRepository.findByCommunityId(c.getId()).stream()
        .anyMatch(cu -> cu.getUser() != null && cu.getUser().getId().equals(user.getId()));

      if (isCreator || isMember) {
        Map<String, Object> m = new HashMap<>();
        m.put("communityId", c.getId());
        m.put("name", c.getName());
        m.put("description", c.getDescription());
        m.put("role", isCreator ? "OWNER" : "MEMBER");
        String mainKey = c.getImageUrl() != null && !c.getImageUrl().isBlank() ? c.getImageUrl() : c.getAvatarUrl();
        if (mainKey == null || mainKey.isBlank()) {
          mainKey = c.getBannerUrl();
        }
        String presigned = communityMediaService.generatePresignedSafely(mainKey);
        m.put("imageUrl", presigned);
        m.put("imageKey", mainKey);
        out.add(m);
      }
    }
    return out;
  }

  private Map<String, Object> buildPagedResponse(Page<Community> page, List<Map<String, Object>> communities) {
    Map<String, Object> body = new HashMap<>();
    body.put("communities", communities);
    body.put("page", page.getNumber());
    body.put("size", page.getSize());
    body.put("totalElements", page.getTotalElements());
    body.put("totalPages", page.getTotalPages());
    return body;
  }
}
