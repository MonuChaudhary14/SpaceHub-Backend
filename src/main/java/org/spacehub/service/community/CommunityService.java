package org.spacehub.service.community;

import org.spacehub.DTO.Community.CommunityMemberDTO;
import org.spacehub.DTO.Community.CommunityMemberRequest;
import org.spacehub.DTO.Community.CommunityPendingRequestDTO;
import org.spacehub.DTO.Community.DeleteCommunityDTO;
import org.spacehub.DTO.Community.JoinCommunity;
import org.spacehub.DTO.CancelJoinRequest;
import org.spacehub.DTO.AcceptRequest;
import org.spacehub.DTO.Community.LeaveCommunity;
import org.spacehub.DTO.Community.RenameRoomRequest;
import org.spacehub.DTO.Community.RolesResponse;
import org.spacehub.DTO.RejectRequest;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.Community.CommunityUser;
import org.spacehub.DTO.Community.CommunityChangeRoleRequest;
import org.spacehub.DTO.Community.PendingRequestUserDTO;
import org.spacehub.entities.Community.Role;
import org.spacehub.entities.User.User;
import org.spacehub.repository.ChatRoom.ChatRoomRepository;
import org.spacehub.repository.community.CommunityRepository;
import org.spacehub.repository.UserRepository;
import org.spacehub.repository.community.CommunityUserRepository;
import org.spacehub.DTO.Community.CommunityBlockRequest;
import org.spacehub.DTO.Community.UpdateCommunityDTO;
import org.spacehub.service.S3Service;
import org.spacehub.service.community.CommunityInterfaces.ICommunityService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import org.spacehub.DTO.Community.CreateRoomRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Transactional
@Service
public class CommunityService implements ICommunityService {

  private final CommunityRepository communityRepository;
  private final UserRepository userRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final CommunityUserRepository communityUserRepository;
  private final S3Service s3Service;

  public static class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
      super(message);
    }
  }

  public CommunityService(CommunityRepository communityRepository, UserRepository userRepository,
                          ChatRoomRepository chatRoomRepository, S3Service s3Service,
                          CommunityUserRepository communityUserRepository) {
    this.communityRepository = communityRepository;
    this.userRepository = userRepository;
    this.chatRoomRepository = chatRoomRepository;
    this.communityUserRepository = communityUserRepository;
    this.s3Service = s3Service;
  }

  @CacheEvict(value = {"communities"}, allEntries = true)
  public ResponseEntity<ApiResponse<Map<String, Object>>> createCommunity(
    String name, String description, String createdByEmail, MultipartFile imageFile) {

    try {
      validateCommunityInputs(name, description, createdByEmail, imageFile);

      User creator = getCreator(createdByEmail);
      validateImage(imageFile);

      String imageKey = uploadCommunityImage(name, imageFile);
      String imageUrl = s3Service.generatePresignedDownloadUrl(imageKey, Duration.ofHours(2));

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

  private void validateCommunityInputs(String name, String description, String email, MultipartFile imageFile) {
    if (name == null || name.isBlank() || description == null || description.isBlank() ||
      email == null || email.isBlank()) {
      throw new IllegalArgumentException("All fields (name, description, createdByEmail) are required");
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

  private String uploadCommunityImage(String name, MultipartFile imageFile) throws IOException {
    String fileName = System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();
    String key = "communities/" + name.replaceAll("[^a-zA-Z0-9]", "_") + "/" + fileName;
    s3Service.uploadFile(key, imageFile.getInputStream(), imageFile.getSize());
    return key;
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
    CommunityUser admin = new CommunityUser();
    admin.setCommunity(community);
    admin.setUser(creator);
    admin.setRole(Role.ADMIN);
    admin.setJoinDate(LocalDateTime.now());
    admin.setBanned(false);

    communityUserRepository.save(admin);

    if (community.getCommunityUsers() == null) {
      community.setCommunityUsers(new HashSet<>());
    }
    community.getCommunityUsers().add(admin);
    communityRepository.save(community);
  }

  @CacheEvict(value = {"communities"}, key = "#deleteCommunity.name")
  public ResponseEntity<?> deleteCommunityByName(@RequestBody DeleteCommunityDTO deleteCommunity) {

    String name = deleteCommunity.getName();
    String userEmail = deleteCommunity.getUserEmail();

    Community community = communityRepository.findByName(name);
    if (community == null) {
      return ResponseEntity.badRequest().body("Community not found");
    }

    Optional<User> userOptional = userRepository.findByEmail(userEmail);
    if (userOptional.isEmpty()) {
      return ResponseEntity.badRequest().body("User not found");
    }

    User user = userOptional.get();

    if (!community.getCreatedBy().getId().equals(user.getId())) {
      return ResponseEntity.status(403).body("You are not authorized to delete this community");
    }

    communityRepository.delete(community);
    return ResponseEntity.ok().body("Community deleted successfully");
  }

  @CachePut(value = "communities", key = "#joinCommunity.communityName")
  public ResponseEntity<ApiResponse<?>> requestToJoinCommunity(@RequestBody JoinCommunity joinCommunity) {

    if (joinCommunity.getCommunityName() == null || joinCommunity.getCommunityName().isEmpty() ||
      joinCommunity.getUserEmail() == null || joinCommunity.getUserEmail().isEmpty()) {

      return ResponseEntity.badRequest().body(
        new ApiResponse<>(400, "Check the fields")
      );
    }

    Community community = communityRepository.findByName(joinCommunity.getCommunityName());

    if (community != null) {
      Optional<User> optionalUser = userRepository.findByEmail(joinCommunity.getUserEmail());

      if (optionalUser.isEmpty()) {
        return ResponseEntity.badRequest().body(
          new ApiResponse<>(400, "User not found")
        );
      }

      User user = optionalUser.get();

      boolean isAlreadyMember = community.getCommunityUsers().stream()
        .anyMatch(cu -> cu.getUser().getId().equals(user.getId()));

      if (isAlreadyMember) {
        return ResponseEntity.status(403).body(
          new ApiResponse<>(403, "You are already in this community")
        );
      }

      community.getPendingRequests().add(user);
      communityRepository.save(community);

      return ResponseEntity.ok().body(
        new ApiResponse<>(200, "Request send to community")
      );
    } else {
      return ResponseEntity.badRequest().body(
        new ApiResponse<>(400, "Community not found")
      );
    }
  }

  @CacheEvict(value = "communities", key = "#cancelJoinRequest.communityName")
  public ResponseEntity<ApiResponse<?>> cancelRequestCommunity(@RequestBody CancelJoinRequest cancelJoinRequest) {

    String communityName = cancelJoinRequest.getCommunityName();
    String userEmail = cancelJoinRequest.getUserEmail();

    if (communityName == null || communityName.isBlank() || userEmail == null || userEmail.isBlank()) {
      return ResponseEntity.badRequest().body(
        new ApiResponse<>(400, "Both communityName and userEmail are required", null)
      );
    }

    Community community = communityRepository.findByName(communityName);
    if (community == null) {
      return ResponseEntity.badRequest().body(
        new ApiResponse<>(400, "Community not found", null)
      );
    }

    Optional<User> optionalUser = userRepository.findByEmail(userEmail);
    if (optionalUser.isEmpty()) {
      return ResponseEntity.badRequest().body(
        new ApiResponse<>(400, "User not found", null)
      );
    }

    User user = optionalUser.get();

    if (!community.getPendingRequests().contains(user)) {
      return ResponseEntity.status(403).body(
        new ApiResponse<>(403, "No join request found for this user in the community", null)
      );
    }

    community.getPendingRequests().remove(user);
    communityRepository.save(community);

    return ResponseEntity.ok(
      new ApiResponse<>(200, "Cancelled the join request successfully", null)
    );
  }

  @CacheEvict(value = "communities", key = "#acceptRequest.communityName")
  public ResponseEntity<ApiResponse<?>> acceptRequest(AcceptRequest acceptRequest) {
    if (isEmpty(acceptRequest.getUserEmail(), acceptRequest.getCommunityName(), acceptRequest.getCreatorEmail())) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields"));
    }

    try {
      String creatorEmail = acceptRequest.getCreatorEmail().trim().toLowerCase();
      String userEmail = acceptRequest.getUserEmail().trim().toLowerCase();
      String communityName = acceptRequest.getCommunityName().trim();

      Community community = findCommunityByName(communityName);
      User creator = findUserByEmail(creatorEmail);
      User user = findUserByEmail(userEmail);

      if (!community.getCreatedBy().getId().equals(creator.getId())) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403,
          "You are not authorized to accept requests"));
      }

      boolean pending = community.getPendingRequests().stream()
        .anyMatch(u -> u != null && u.getId() != null && u.getId().equals(user.getId()));

      if (!pending) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400,
          "No pending request from this user"));
      }

      community.getPendingRequests().removeIf(u -> u != null && u.getId() != null &&
        u.getId().equals(user.getId()));
      communityRepository.save(community);

      CommunityUser newMember = new CommunityUser();
      newMember.setCommunity(community);
      newMember.setUser(user);
      newMember.setRole(Role.MEMBER);
      newMember.setJoinDate(LocalDateTime.now());
      newMember.setBanned(false);
      communityUserRepository.save(newMember);

      if (community.getCommunityUsers() == null) {
        community.setCommunityUsers(new HashSet<>());
      }
      community.getCommunityUsers().add(newMember);
      communityRepository.save(community);

      return ResponseEntity.ok(new ApiResponse<>(200,
        "User has been added to the community successfully", null));
    } catch (ResourceNotFoundException ex) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, ex.getMessage()));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: "
        + e.getMessage(), null));
    }
  }

  private boolean isEmpty(String... values) {
    for (String val : values) {
      if (val == null || val.isBlank()) return true;
    }
    return false;
  }

  @CacheEvict(value = "communities", key = "#leaveCommunity.communityName")
  public ResponseEntity<?> leaveCommunity(LeaveCommunity leaveCommunity) {
    if (isInvalidLeaveRequest(leaveCommunity)) {
      return badRequest("Check the fields");
    }

    try {
      Community community = findCommunityByName(leaveCommunity.getCommunityName());
      User user = findUserByEmail(leaveCommunity.getUserEmail());

      if (isCreatorOfCommunity(community, user)) {
        return forbidden();
      }

      Optional<CommunityUser> communityUserOptional = Optional.ofNullable(findCommunityUser(community, user));

      if (communityUserOptional.isEmpty()) {
        return badRequest("You are not a member of this community");
      }

      removeCommunityUser(community, communityUserOptional.get(), user);
      communityRepository.save(community);

      return ok();

    } catch (ResourceNotFoundException ex) {
      return badRequest(ex.getMessage());
    } catch (Exception e) {
      return serverError("Unexpected error: " + e.getMessage());
    }
  }

  private boolean isInvalidLeaveRequest(LeaveCommunity leaveCommunity) {
    return isEmpty(leaveCommunity.getCommunityName(), leaveCommunity.getUserEmail());
  }

  private boolean isCreatorOfCommunity(Community community, User user) {
    return community.getCreatedBy().getId().equals(user.getId());
  }

  private void removeCommunityUser(Community community, CommunityUser toRemove, User user) {
    communityUserRepository.delete(toRemove);
    if (community.getCommunityUsers() != null) {
      community.getCommunityUsers().removeIf(cu ->
        (cu.getId() != null && cu.getId().equals(toRemove.getId())) ||
          (cu.getUser() != null && cu.getUser().getId().equals(user.getId()))
      );
    }
  }

  private ResponseEntity<ApiResponse<?>> ok() {
    return ResponseEntity.ok(new ApiResponse<>(200, "You have left the community successfully",
      null));
  }

  @CacheEvict(value = "communities", key = "#rejectRequest.communityName")
  public ResponseEntity<?> rejectRequest(RejectRequest rejectRequest) {

    ResponseEntity<ApiResponse<Object>> validationResponse = validateRejectRequest(rejectRequest);
    if (validationResponse != null) return validationResponse;

    try {
      Community community = findCommunity(rejectRequest.getCommunityName());
      User creator = findUser(rejectRequest.getCreatorEmail(), "Creator not found");
      User user = findUser(rejectRequest.getUserEmail(), "User not found");

      if (!community.getCreatedBy().getId().equals(creator.getId())) {
        return forbidden();
      }

      if (!community.getPendingRequests().contains(user)) {
        return badRequest("No pending request from this user");
      }

      community.getPendingRequests().remove(user);
      communityRepository.save(community);

      return ok("Join request rejected successfully");

    } catch (IllegalArgumentException e) {
      return badRequest(e.getMessage());
    } catch (Exception e) {
      return internalError("An unexpected error occurred: " + e.getMessage());
    }
  }

  private ResponseEntity<ApiResponse<Object>> validateRejectRequest(RejectRequest req) {
    if (isBlank(req.getCommunityName()) || isBlank(req.getUserEmail()) || isBlank(req.getCreatorEmail())) {
      return badRequest("Check the fields");
    }
    return null;
  }

  private Community findCommunity(String name) {
    Community community = communityRepository.findByName(name);
    if (community == null) throw new IllegalArgumentException("Community not found");
    return community;
  }

  private boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private ResponseEntity<ApiResponse<?>> internalError(String msg) {
    return ResponseEntity.internalServerError().body(new ApiResponse<>(500, msg, null));
  }

  private Community findCommunityByName(String name) {
    Community community = communityRepository.findByName(name);
    if (community == null) {
      throw new ResourceNotFoundException("Community not found");
    }
    return community;
  }

  private User findUserByEmail(String email) {
    return userRepository.findByEmail(email)
      .orElseThrow(() -> new ResourceNotFoundException("User not found"));
  }

  public ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityWithRooms(UUID communityId) {

    Optional<Community> optionalCommunity = communityRepository.findById(communityId);
    if (optionalCommunity.isEmpty()) {
      return ResponseEntity.badRequest().body(
              new ApiResponse<>(400, "Community not found", null)
      );
    }

    Community community = optionalCommunity.get();

    List<ChatRoom> rooms = chatRoomRepository.findByCommunityId(communityId);

    Map<String, Object> response = new HashMap<>();
    response.put("communityId", community.getId());
    response.put("communityName", community.getName());
    response.put("description", community.getDescription());
    response.put("rooms", rooms);

    List<Map<String, Object>> members = new ArrayList<>();
    for (CommunityUser communityUser : community.getCommunityUsers()) {
      Map<String, Object> memberData = new HashMap<>();
      memberData.put("email", communityUser.getUser().getEmail());
      memberData.put("role", communityUser.getRole());
      members.add(memberData);
    }
    response.put("members", members);

    return ResponseEntity.ok(
            new ApiResponse<>(200, "Community details fetched successfully", response)
    );
  }

  public ResponseEntity<ApiResponse<String>> removeMemberFromCommunity(CommunityMemberRequest request) {

    if (request.getCommunityId() == null || request.getUserEmail() == null || request.getRequesterEmail() == null) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields", null));
    }

    Community community = communityRepository.findById(request.getCommunityId()).orElse(null);
    if (community == null) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400,
        "Community not found", null));
    }

    Optional<User> optionalRequester = userRepository.findByEmail(request.getRequesterEmail());
    Optional<User> optionalTarget = userRepository.findByEmail(request.getUserEmail());
    if (optionalRequester.isEmpty() || optionalTarget.isEmpty()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));
    }

    User requester = optionalRequester.get();
    User target = optionalTarget.get();

    if (!community.getCreatedBy().getId().equals(requester.getId())) {
      return ResponseEntity.status(403).body(new ApiResponse<>(403,
        "Only the community creator can remove members", null));
    }

    Optional<CommunityUser> communityUserOptional = community.getCommunityUsers()
      .stream().filter(communityUser ->
        communityUser.getUser().getId().equals(target.getId()))
      .findFirst();

    if (communityUserOptional.isEmpty()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User is not a member",
        null));
    }

    CommunityUser userToRemove = communityUserOptional.get();
    communityUserRepository.delete(userToRemove);

    return ResponseEntity.ok(new ApiResponse<>(200, "Member removed successfully", null));
  }

  public ResponseEntity<ApiResponse<String>> changeMemberRole(CommunityChangeRoleRequest request) {
    try {
      validateChangeRoleRequest(request);

      Community community = findCommunity(request.getCommunityId());
      User requester = findUser(request.getRequesterEmail(), "Requester not found");
      User target = findUser(request.getTargetUserEmail(), "Target user not found");

      verifyCreatorPermission(community, requester);

      CommunityUser communityUser = findCommunityUser(community, target);
      Role newRole = parseRole(request.getNewRole());

      communityUser.setRole(newRole);
      communityUserRepository.save(communityUser);

      return ResponseEntity.ok(new ApiResponse<>(
        200,
        "Role of " + target.getEmail() + " changed to " + newRole,
        null
      ));

    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    } catch (SecurityException e) {
      return ResponseEntity.status(403).body(new ApiResponse<>(403, e.getMessage(), null));
    } catch (Exception e) {
      return ResponseEntity.internalServerError()
        .body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  private void validateChangeRoleRequest(CommunityChangeRoleRequest request) {
    if (request.getCommunityId() == null ||
      request.getTargetUserEmail() == null ||
      request.getRequesterEmail() == null ||
      request.getNewRole() == null) {
      throw new IllegalArgumentException("Check the fields");
    }
  }

  private Community findCommunity(UUID communityId) {
    return communityRepository.findById(communityId)
      .orElseThrow(() -> new IllegalArgumentException("Community not found"));
  }

  private User findUser(String email, String errorMessage) {
    return userRepository.findByEmail(email)
      .orElseThrow(() -> new IllegalArgumentException(errorMessage));
  }

  private void verifyCreatorPermission(Community community, User requester) {
    if (!community.getCreatedBy().getId().equals(requester.getId())) {
      throw new SecurityException("Only the community creator can change roles");
    }
  }

  private CommunityUser findCommunityUser(Community community, User target) {
    return community.getCommunityUsers().stream()
      .filter(cu -> cu.getUser().getId().equals(target.getId()))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("User is not a member"));
  }

  private Role parseRole(String roleStr) {
    try {
      return Role.valueOf(roleStr.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid role: " + roleStr);
    }
  }

  public ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityMembers(UUID communityId) {
    Optional<Community> optionalCommunity = communityRepository.findById(communityId);
    if (optionalCommunity.isEmpty()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));
    }

    Community community = optionalCommunity.get();

    List<CommunityUser> communityUsers = communityUserRepository.findByCommunityId(communityId);

    List<CommunityMemberDTO> members = communityUsers.stream()
      .map(communityUser -> {
        User user = communityUser.getUser();
        return CommunityMemberDTO.builder()
          .memberId(user.getId())
          .username(user.getUsername())
          .email(user.getEmail())
          .role(communityUser.getRole())
          .joinDate(communityUser.getJoinDate())
          .isBanned(communityUser.isBanned())
          .avatarKey(user.getAvatarUrl())
          .avatarPreviewUrl(generatePresignedUrlSafely(user.getAvatarUrl()))
          .bio(user.getBio())
          .location(user.getLocation())
          .website(user.getWebsite())
          .build();
      })
      .collect(Collectors.toList());


    Map<String, Object> response = new HashMap<>();
    response.put("communityId", community.getId());
    response.put("communityName", community.getName());
    response.put("totalMembers", members.size());
    response.put("members", members);

    return ResponseEntity.ok(
            new ApiResponse<>(200, "Community members fetched successfully", response)
    );
  }

  public ResponseEntity<ApiResponse<String>> blockOrUnblockMember(CommunityBlockRequest request) {

    if (request.getCommunityId() == null || request.getRequesterEmail() == null ||
      request.getTargetUserEmail() == null) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields", null));
    }

    Optional<Community> optionalCommunity = communityRepository.findById(request.getCommunityId());

    if (optionalCommunity.isEmpty()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400,
        "Community not found", null));
    }

    Community community = optionalCommunity.get();

    Optional<User> optionalRequester = userRepository.findByEmail(request.getRequesterEmail());
    Optional<User> optionalTarget = userRepository.findByEmail(request.getTargetUserEmail());

    if (optionalRequester.isEmpty() || optionalTarget.isEmpty()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));
    }

    User requester = optionalRequester.get();
    User target = optionalTarget.get();

    if (!community.getCreatedBy().getId().equals(requester.getId())) {
      return ResponseEntity.status(403).body(new ApiResponse<>(403,
        "Only community creator can block or unblock members", null));
    }

    Optional<CommunityUser> optionalCommunityUser = community.getCommunityUsers().stream()
      .filter(communityUser -> communityUser.getUser().getId().equals(target.getId()))
      .findFirst();

    if (optionalCommunityUser.isEmpty()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400,
        "User is not a member of this community", null));
    }

    CommunityUser communityUser = optionalCommunityUser.get();

    communityUser.setBanned(request.isBlock());
    communityUserRepository.save(communityUser);

    String blocked = request.isBlock() ? "blocked" : "unblocked";
    return ResponseEntity.ok(new ApiResponse<>(200, "User " + target.getEmail() + " has been " +
      blocked + " successfully", null));
  }

  public ResponseEntity<ApiResponse<Community>> updateCommunityInfo(UpdateCommunityDTO dto) {

    Optional<Community> optionalCommunity = communityRepository.findById(dto.getCommunityId());
    if (optionalCommunity.isEmpty()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found",
        null));
    }

    Community community = optionalCommunity.get();

    if (dto.getName() != null) community.setName(dto.getName());
    if (dto.getDescription() != null) community.setDescription(dto.getDescription());

    communityRepository.save(community);

    return ResponseEntity.ok(new ApiResponse<>(200,
      "Community info updated successfully", community));
  }

  private void validateImage(MultipartFile file) {
    if (file.isEmpty()) throw new RuntimeException("File is empty");

    if (file.getSize() > 2 * 1024 * 1024)
      throw new RuntimeException("File size exceeds 2 MB");

    String contentType = file.getContentType();

    if (contentType == null || !contentType.startsWith("image/"))
      throw new RuntimeException("Only image files are allowed");
  }

  @Override
  public ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> listAllCommunities() {

    List<Community> all = communityRepository.findAll();

    List<Map<String, Object>> out = all.stream().map(c -> {
      Map<String, Object> m = new HashMap<>();
      m.put("communityId", c.getId());
      m.put("name", c.getName());
      m.put("description", c.getDescription());

      String key = c.getImageUrl();
      if (key != null && !key.isBlank()) {
        try {
          String presigned = s3Service.generatePresignedDownloadUrl(key, Duration.ofHours(1));
          m.put("imageUrl", presigned);
          m.put("imageKey", key);
        } catch (Exception e) {
          m.put("imageUrl", null);
          m.put("imageKey", key);
        }
      } else {
        m.put("imageUrl", null);
      }

      String bannerKey = c.getBannerUrl();
      if (bannerKey != null && !bannerKey.isBlank()) {
        try {
          String presigned = s3Service.generatePresignedDownloadUrl(bannerKey, Duration.ofHours(1));
          m.put("bannerUrl", presigned);
        } catch (Exception e) {
          m.put("bannerUrl", null);
        }
      } else {
        m.put("bannerUrl", null);
      }

      return m;
    }).toList();

    return ResponseEntity.ok(new ApiResponse<>(200, "Communities fetched",
      Map.of("communities", out)));
  }

  public ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> listMyCommunities(
    String requesterEmail) {
    try {
      if (isInvalidEmail(requesterEmail)) {
        return ResponseEntity.badRequest()
          .body(new ApiResponse<>(400, "Requester email is required"));
      }

      String normalizedEmail = requesterEmail.trim().toLowerCase();
      Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);

      if (userOpt.isEmpty()) {
        return ResponseEntity.ok(
          new ApiResponse<>(200, "User not found, no communities fetched",
            Map.of("communities", List.of()))
        );
      }

      List<Map<String, Object>> userCommunities = buildCommunityListForUser(normalizedEmail);
      return ResponseEntity.ok(
        new ApiResponse<>(200, "User's communities fetched", Map.of("communities", userCommunities))
      );

    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(
        new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null)
      );
    }
  }

  private boolean isInvalidEmail(String email) {
    return email == null || email.isBlank();
  }

  private List<Map<String, Object>> buildCommunityListForUser(String reqEmailLower) {
    List<Community> allCommunities = communityRepository.findAll();
    Map<UUID, Map<String, Object>> dedup = new LinkedHashMap<>();

    for (Community c : allCommunities) {
      if (isUserInCommunity(c, reqEmailLower)) {
        dedup.put(c.getId(), buildCommunityMap(c));
      }
    }

    return new ArrayList<>(dedup.values());
  }

  private boolean isUserInCommunity(Community c, String reqEmailLower) {
    boolean matchesCreator = c.getCreatedBy() != null &&
      c.getCreatedBy().getEmail() != null &&
      c.getCreatedBy().getEmail().equalsIgnoreCase(reqEmailLower);

    boolean matchesMember = c.getCommunityUsers() != null &&
      c.getCommunityUsers().stream()
        .anyMatch(cu -> cu.getUser() != null &&
          cu.getUser().getEmail() != null &&
          cu.getUser().getEmail().equalsIgnoreCase(reqEmailLower));

    return matchesCreator || matchesMember;
  }

  private Map<String, Object> buildCommunityMap(Community c) {
    Map<String, Object> m = new HashMap<>();
    m.put("communityId", c.getId());
    m.put("name", c.getName());
    m.put("description", c.getDescription());

    m.put("imageUrl", generatePresignedUrlSafely(c.getImageUrl()));
    m.put("imageKey", c.getImageUrl());
    m.put("bannerUrl", generatePresignedUrlSafely(c.getBannerUrl()));

    return m;
  }

  private String generatePresignedUrlSafely(String key) {
    if (key == null || key.isBlank()) return null;
    try {
      return s3Service.generatePresignedDownloadUrl(key, Duration.ofHours(1));
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityDetailsWithAdminFlag(
          UUID communityId, String requesterEmail) {

    if (communityId == null || requesterEmail == null || requesterEmail.isBlank()) {
      return ResponseEntity.badRequest()
              .body(new ApiResponse<>(400, "Community ID and requester email are required", null));
    }

    Optional<Community> optionalCommunity = communityRepository.findById(communityId);
    if (optionalCommunity.isEmpty()) {
      return ResponseEntity.badRequest()
              .body(new ApiResponse<>(400, "Community not found", null));
    }
    Community community = optionalCommunity.get();

    boolean isAdmin = false;
    Optional<User> optionalUser = userRepository.findByEmail(requesterEmail.trim().toLowerCase());
    if (optionalUser.isPresent()) {
      User user = optionalUser.get();
      isAdmin = isUserAdminInCommunity(community, user);
    }

    List<ChatRoom> rooms = chatRoomRepository.findByCommunityId(communityId);

    Map<String, Object> response = new HashMap<>();
    response.put("communityId", community.getId());  // UUID
    response.put("communityName", community.getName());
    response.put("description", community.getDescription());
    response.put("isAdmin", isAdmin);
    response.put("rooms", rooms);

    List<Map<String, Object>> members = new ArrayList<>();
    for (CommunityUser communityUser : community.getCommunityUsers()) {
      Map<String, Object> memberData = new HashMap<>();
      memberData.put("email", communityUser.getUser().getEmail());
      memberData.put("role", communityUser.getRole());
      members.add(memberData);
    }
    response.put("members", members);

    return ResponseEntity.ok(new ApiResponse<>(200, "Community details fetched", response));
  }

  private boolean isUserAdminInCommunity(Community community, User user) {
    if (community.getCreatedBy() != null && community.getCreatedBy().getId().equals(user.getId())) {
      return true;
    }
    return community.getCommunityUsers().stream()
      .anyMatch(cu -> cu.getUser().getId().equals(user.getId()) && cu.getRole() == Role.ADMIN);
  }

  public ResponseEntity<?> createRoomInCommunity(CreateRoomRequest request) {
    try {

      if (request.getRequesterEmail() == null || request.getRequesterEmail().isBlank()) {
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(400, "Requester email is required", null));
      }

      Community community = communityRepository.findById(request.getCommunityId())
        .orElseThrow(() -> new ResourceNotFoundException("Community not found with ID: " +
          request.getCommunityId()));

      User requester = userRepository.findByEmail(request.getRequesterEmail())
        .orElseThrow(() -> new ResourceNotFoundException("Requester user not found with email: " +
          request.getRequesterEmail()));

      if (!isUserAdminInCommunity(community, requester)) {
        return ResponseEntity.status(403)
          .body(new ApiResponse<>(403, "You are not authorized to create a room in this community",
            null));
      }

      if (request.getRoomName() == null || request.getRoomName().isBlank()) {
        return ResponseEntity.badRequest()
          .body(new ApiResponse<>(400, "Room name cannot be empty", null));
      }

      boolean roomExists = chatRoomRepository.findByCommunityId(community.getId())
        .stream()
        .anyMatch(room -> room.getName().equalsIgnoreCase(request.getRoomName()));

      if (roomExists) {
        return ResponseEntity.badRequest()
          .body(new ApiResponse<>(400, "A room with this name already exists", null));
      }

      ChatRoom newRoom = new ChatRoom();
      newRoom.setName(request.getRoomName().trim());
      newRoom.setCommunity(community);

      UUID code = UUID.randomUUID();
      newRoom.setRoomCode(code);

      ChatRoom savedRoom = chatRoomRepository.save(newRoom);

      return ResponseEntity.status(201)
        .body(new ApiResponse<>(201, "Room created successfully", savedRoom));

    } catch (ResourceNotFoundException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500,
        "An unexpected error occurred: " + e.getMessage(), null));
    }
  }

  @Override
  public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRoomsByCommunity(UUID communityId) {
    if (communityId == null) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community ID is required", null));
    }

    Optional<Community> optionalCommunity = communityRepository.findById(communityId);
    if (optionalCommunity.isEmpty()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));
    }

    List<ChatRoom> rooms = chatRoomRepository.findByCommunityId(communityId);

    List<Map<String, Object>> out = rooms.stream()
            .map(r -> {
              Map<String, Object> m = new HashMap<>();
              m.put("id", r.getId());
              m.put("name", r.getName());
              m.put("roomCode", r.getRoomCode());
              return m;
            }).collect(Collectors.toList());

    return ResponseEntity.ok(new ApiResponse<>(200, "Rooms fetched successfully", out));
  }

  @Override
  public ResponseEntity<?> deleteRoom(UUID communityId, UUID roomId, String requesterEmail) {
    try {
      ChatRoom room = chatRoomRepository.findById(roomId)
        .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + roomId));

      Community community = room.getCommunity();
      if (community == null || !community.getId().equals(communityId)) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400,
          "Room does not belong to the specified community", null));
      }

      User requester = userRepository.findByEmail(requesterEmail)
        .orElseThrow(() -> new ResourceNotFoundException("Requester not found with email: " + requesterEmail));

      if (!isUserAdminInCommunity(community, requester)) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403,
          "You are not authorized to delete this room", null));
      }

      chatRoomRepository.delete(room);
      return ResponseEntity.ok(new ApiResponse<>(200, "Room deleted successfully", null));
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: "
        + e.getMessage(), null));
    }
  }

  public ResponseEntity<?> searchCommunities(String q, String requesterEmail, int page, int size) {
    if (q == null || q.isBlank()) {
      return listAllCommunities();
    }

    Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
    Page<Community> communityPage = communityRepository
      .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(q, q, pageable);

    User requester = null;
    if (requesterEmail != null && !requesterEmail.isBlank()) {
      requester = userRepository.findByEmail(requesterEmail).orElse(null);
    }
    final User finalRequester = requester;

    List<Map<String, Object>> results = communityPage.getContent().stream().map(c -> {
      Map<String, Object> m = new HashMap<>();
      m.put("communityId", c.getId());
      m.put("name", c.getName());
      m.put("description", c.getDescription());

      String key = c.getImageUrl();
      if (key != null && !key.isBlank()) {
        try {
          String presigned = s3Service.generatePresignedDownloadUrl(key, Duration.ofHours(1));
          m.put("imageUrl", presigned);
          m.put("imageKey", key);
        } catch (Exception e) {
          m.put("imageUrl", null);
          m.put("imageKey", key);
        }
      } else {
        m.put("imageUrl", null);
      }

      if (finalRequester != null) {
        boolean isMember = c.getCommunityUsers().stream()
          .anyMatch(cu -> cu.getUser().getId().equals(finalRequester.getId()));
        boolean isRequested = c.getPendingRequests().stream()
          .anyMatch(u -> u.getId().equals(finalRequester.getId()));
        m.put("isMember", isMember);
        m.put("isRequested", isRequested);
      }

      return m;
    }).collect(Collectors.toList());

    Map<String, Object> body = new HashMap<>();
    body.put("communities", results);
    body.put("page", communityPage.getNumber());
    body.put("size", communityPage.getSize());
    body.put("totalElements", communityPage.getTotalElements());
    body.put("totalPages", communityPage.getTotalPages());

    return ResponseEntity.ok(new ApiResponse<>(200, "Search results", body));
  }

  @Override
  public ResponseEntity<?> enterOrRequestCommunity(UUID communityId, String requesterEmail) {
    if (requesterEmail == null || requesterEmail.isBlank()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "requesterEmail is required", null));
    }

    Optional<Community> optionalCommunity = communityRepository.findById(communityId);
    if (optionalCommunity.isEmpty()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));
    }

    Community community = optionalCommunity.get();

    Optional<User> optionalUser = userRepository.findByEmail(requesterEmail);
    if (optionalUser.isEmpty()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));
    }

    User user = optionalUser.get();

    boolean isMember = community.getCommunityUsers().stream()
            .anyMatch(cu -> cu.getUser().getId().equals(user.getId()));

    boolean isPending = community.getPendingRequests().stream()
            .anyMatch(u -> u.getId().equals(user.getId()));

    if (isMember) {
      return getCommunityWithRooms(communityId);
    }
    else {
      if (isPending) {
        return ResponseEntity.ok(new ApiResponse<>(200, "Join request already pending",
                Map.of("requested", true, "message", "Join request already pending")));
      }
      else {
        community.getPendingRequests().add(user);
        communityRepository.save(community);
        return ResponseEntity.ok(new ApiResponse<>(200, "Join request sent",
                Map.of("requested", true, "message", "Join request sent")));
      }
    }
  }

  @Override
  public ResponseEntity<?> uploadCommunityAvatar(UUID communityId, String requesterEmail,
                                                 MultipartFile imageFile) {
    if (requesterEmail == null || requesterEmail.isBlank()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "requesterEmail is required", null));
    }

    if (imageFile == null || imageFile.isEmpty()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Image file is required", null));
    }

    try {
      Optional<Community> optionalCommunity = communityRepository.findById(communityId);
      if (optionalCommunity.isEmpty()) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));
      }

      Community community = optionalCommunity.get();

      Optional<User> optionalUser = userRepository.findByEmail(requesterEmail);
      if (optionalUser.isEmpty()) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));
      }

      User requester = optionalUser.get();

      if (!isUserAdminInCommunity(community, requester)) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403, "Only community admin can change avatar", null));
      }

      validateImage(imageFile);

      String fileName = System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();
      String key = "communities/" + community.getName().replaceAll("[^a-zA-Z0-9]", "_") + "/avatar/" + fileName;

      s3Service.uploadFile(key, imageFile.getInputStream(), imageFile.getSize());
      community.setImageUrl(key);
      communityRepository.save(community);

      String presigned = s3Service.generatePresignedDownloadUrl(key, Duration.ofHours(2));
      Map<String, Object> body = Map.of("presignedUrl", presigned, "key", key);

      return ResponseEntity.ok(new ApiResponse<>(200, "Avatar updated successfully", body));
    }
    catch (IOException e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Error uploading image: " + e.getMessage(), null));
    }
    catch (RuntimeException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    }
  }

  @Override
  public ResponseEntity<?> uploadCommunityBanner(
    UUID communityId,
    String requesterEmail,
    MultipartFile bannerFile,
    MultipartFile communityAvatarFile,
    MultipartFile userAvatarFile,
    String newName,
    String newDescription
  ) {
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
      if (community.getCreatedBy() != null)
        body.put("createdBy", community.getCreatedBy().getEmail());
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

  private void handleImageUploads(
    Community community,
    User requester,
    MultipartFile bannerFile,
    MultipartFile communityAvatarFile,
    MultipartFile userAvatarFile,
    Map<String, Object> body
  ) throws IOException {
    String safeCommunityName = safeName(community.getName());

    uploadCommunityAvatar(community, communityAvatarFile, safeCommunityName, body);
    uploadCommunityBanner(community, bannerFile, safeCommunityName, body);
    uploadUserAvatar(requester, userAvatarFile, body);
  }

  private void uploadCommunityAvatar(Community community, MultipartFile file, String safeName,
                                     Map<String, Object> body) throws IOException {
    if (file == null || file.isEmpty()) return;

    String avatarKey = uploadAndReturnKey(file, "communities/" + safeName + "/avatar/");
    community.setImageUrl(avatarKey);
    body.put("communityAvatarKey", avatarKey);
    body.put("communityAvatarUrl", tryGeneratePresigned(avatarKey));
  }

  private void uploadCommunityBanner(Community community, MultipartFile file, String safeName,
                                     Map<String, Object> body) throws IOException {
    if (file == null || file.isEmpty()) return;

    String bannerKey = uploadAndReturnKey(file, "communities/" + safeName + "/banner/");
    community.setBannerUrl(bannerKey);
    body.put("bannerKey", bannerKey);
    body.put("bannerUrl", tryGeneratePresigned(bannerKey));
  }

  private void uploadUserAvatar(User requester, MultipartFile file, Map<String, Object> body) throws IOException {
    if (file == null || file.isEmpty()) return;

    String userKey = uploadAndReturnKey(file, "users/" + requester.getId() + "/avatar/");
    requester.setAvatarUrl(userKey);
    userRepository.save(requester);
    body.put("userAvatarKey", userKey);
    body.put("userAvatarUrl", tryGeneratePresigned(userKey));
  }

  private ResponseEntity<ApiResponse<Object>> ok(Object data) {
    return ResponseEntity.ok(new ApiResponse<>(200, "Banner applied successfully", data));
  }

  private ResponseEntity<ApiResponse<Object>> badRequest(String message) {
    return ResponseEntity.badRequest().body(new ApiResponse<>(400, message, null));
  }

  private ResponseEntity<ApiResponse<Object>> forbidden() {
    return ResponseEntity.status(403).body(new ApiResponse<>(403, "Only community admin can change banner/info", null));
  }

  private ResponseEntity<ApiResponse<Object>> serverError(String message) {
    return ResponseEntity.internalServerError().body(new ApiResponse<>(500, message, null));
  }

  private Community getCommunityOrThrow(UUID communityId) {
    return communityRepository.findById(communityId)
      .orElseThrow(() -> new ResourceNotFoundException("Community not found"));
  }

  private User getUserOrThrow(String email) {
    return userRepository.findByEmail(email)
      .orElseThrow(() -> new ResourceNotFoundException("User not found"));
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

  private String uploadAndReturnKey(MultipartFile file, String prefix) throws IOException {
    validateImage(file);
    String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
    String key = prefix + fileName;
    s3Service.uploadFile(key, file.getInputStream(), file.getSize());
    return key;
  }

  private String tryGeneratePresigned(String key) {
    try {
      return s3Service.generatePresignedDownloadUrl(key, Duration.ofHours(2));
    } catch (Exception ignored) {
      return null;
    }
  }

  private String safeName(String name) {
    if (name == null) return "community";
    return name.replaceAll("[^a-zA-Z0-9]", "_");
  }

  @Override
  public ResponseEntity<?> renameRoomInCommunity(UUID communityId, UUID roomId, RenameRoomRequest req) {
    if (req.getRequesterEmail() == null || req.getRequesterEmail().isBlank() ||
            req.getNewRoomName() == null || req.getNewRoomName().isBlank()) {
      return ResponseEntity.badRequest().body(
              new ApiResponse<>(400, "requesterEmail and newRoomName are required", null)
      );
    }

    try {
      Community community = communityRepository.findById(communityId)
              .orElseThrow(() -> new ResourceNotFoundException("Community not found"));
      User requester = userRepository.findByEmail(req.getRequesterEmail())
              .orElseThrow(() -> new ResourceNotFoundException("User not found"));

      if (!isUserAdminInCommunity(community, requester)) {
        return ResponseEntity.status(403).body(
                new ApiResponse<>(403, "Only admin can rename rooms", null)
        );
      }

      ChatRoom room = chatRoomRepository.findById(roomId)
              .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

      if (!room.getCommunity().getId().equals(communityId)) {
        return ResponseEntity.badRequest().body(
                new ApiResponse<>(400, "Room doesn't belong to the community", null)
        );
      }

      boolean exists = chatRoomRepository.findByCommunityId(communityId)
              .stream()
              .anyMatch(r -> r.getName().equalsIgnoreCase(req.getNewRoomName().trim())
                      && !r.getId().equals(roomId));
      if (exists) {
        return ResponseEntity.badRequest().body(
                new ApiResponse<>(400, "Another room with this name already exists", null)
        );
      }

      room.setName(req.getNewRoomName().trim());
      chatRoomRepository.save(room);
      return ResponseEntity.ok(new ApiResponse<>(200, "Room renamed successfully", room));
    }
    catch (ResourceNotFoundException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    }
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(
              new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null)
      );
    }
  }

  @Override
  public ResponseEntity<?> getRolesForRequester(UUID communityId, String requesterEmail) {
    if (communityId == null || requesterEmail == null || requesterEmail.isBlank()) {
      return ResponseEntity.badRequest().body(
              new ApiResponse<>(400, "communityId and requesterEmail are required", null)
      );
    }

    Community community = communityRepository.findById(communityId).orElse(null);
    if (community == null) {
      return ResponseEntity.badRequest().body(
              new ApiResponse<>(400, "Community not found", null)
      );
    }

    User requester = userRepository.findByEmail(requesterEmail).orElse(null);
    if (requester == null) {
      return ResponseEntity.badRequest().body(
              new ApiResponse<>(400, "User not found", null)
      );
    }

    boolean isAdmin = isUserAdminInCommunity(community, requester);

    return ResponseEntity.ok(
            new ApiResponse<>(200, "Roles fetched", new RolesResponse(isAdmin))
    );
  }

  public ResponseEntity<ApiResponse<Map<String, Object>>> discoverCommunities(int page, int size) {
    Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
    Page<Community> communityPage = communityRepository.findAll(pageable);

    List<Map<String, Object>> out = communityPage.getContent().stream().map(c -> {
      Map<String, Object> m = new HashMap<>();
      m.put("communityId", c.getId());
      m.put("name", c.getName());
      m.put("description", c.getDescription());

      String bannerKey = c.getBannerUrl();
      if (bannerKey == null || bannerKey.isBlank()) {
        bannerKey = null;
      }

      String imageKey = c.getImageUrl();
      if (imageKey == null || imageKey.isBlank()) {
        imageKey = null;
      }

      if (bannerKey != null) {
        try {
          String presignedBanner = s3Service.generatePresignedDownloadUrl(bannerKey, Duration.ofHours(1));
          m.put("bannerUrl", presignedBanner);
          m.put("bannerKey", bannerKey);
        } catch (Exception e) {
          m.put("bannerUrl", null);
          m.put("bannerKey", bannerKey);
        }
      } else {
        m.put("bannerUrl", null);
        m.put("bannerKey", null);
      }

      if (imageKey != null) {
        try {
          String presignedImage = s3Service.generatePresignedDownloadUrl(imageKey, Duration.ofHours(1));
          m.put("imageUrl", presignedImage);
          m.put("imageKey", imageKey);
        } catch (Exception e) {
          m.put("imageUrl", null);
          m.put("imageKey", imageKey);
        }
      } else {
        m.put("imageUrl", null);
        m.put("imageKey", null);
      }

      if (c.getCreatedBy() != null) {
        m.put("createdBy", c.getCreatedBy().getEmail());
      }
      m.put("createdAt", c.getCreatedAt());

      return m;
    }).collect(Collectors.toList());

    Map<String, Object> body = new HashMap<>();
    body.put("communities", out);
    body.put("page", communityPage.getNumber());
    body.put("size", communityPage.getSize());
    body.put("totalElements", communityPage.getTotalElements());
    body.put("totalPages", communityPage.getTotalPages());

    return ResponseEntity.ok(new ApiResponse<>(200, "Discover communities fetched", body));
  }

  @Override
  public ResponseEntity<ApiResponse<?>> getPendingRequests(UUID communityId, String requesterEmail) {
    try {
      if (communityId == null || requesterEmail == null || requesterEmail.isBlank()) {
        return ResponseEntity.badRequest().body(
                new ApiResponse<>(400, "communityId and requesterEmail are required", null)
        );
      }

      Community community = communityRepository.findById(communityId)
        .orElseThrow(() -> new ResourceNotFoundException("Community not found with ID: " + communityId));

      User requester = userRepository.findByEmail(requesterEmail)
        .orElseThrow(() -> new ResourceNotFoundException("Requester not found with email: " + requesterEmail));

      Optional<CommunityUser> communityUserOptional = community.getCommunityUsers()
              .stream().filter(cu -> cu.getUser().getId().equals(requester.getId()))
              .findFirst();

      if (communityUserOptional.isEmpty() || communityUserOptional.get().getRole() != Role.ADMIN) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(new ApiResponse<>(403, "You must be an admin to view pending requests", null));
      }

      List<PendingRequestUserDTO> pendingRequests = community.getPendingRequests().stream()
        .map(user -> new PendingRequestUserDTO(
          user.getId(),
          user.getUsername(),
          user.getEmail()
        ))
        .collect(Collectors.toList());

      return ResponseEntity.ok(new ApiResponse<>(200,
              "Pending requests fetched successfully",
              pendingRequests));

    }
    catch (ResourceNotFoundException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
        new ApiResponse<>(404, ex.getMessage(), null)
      );
    }
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(
              new ApiResponse<>(500, "An unexpected error occurred: " + e.getMessage(), null)
      );
    }
  }

  public ResponseEntity<ApiResponse<?>> getAllPendingRequestsForAdmin(String requesterEmail) {
    try {
      User adminUser = userRepository.findByEmail(requesterEmail)
        .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + requesterEmail));

      List<CommunityUser> adminRoles = communityUserRepository.findByUserAndRole(adminUser, Role.ADMIN);
      List<CommunityPendingRequestDTO> allRequests = new ArrayList<>();

      for (CommunityUser adminRole : adminRoles) {
        Community community = adminRole.getCommunity();

        List<PendingRequestUserDTO> pendingRequests = community.getPendingRequests().stream()
          .map(user -> new PendingRequestUserDTO(
            user.getId(),
            user.getUsername(),
            user.getEmail()
          ))
          .collect(Collectors.toList());

        if (!pendingRequests.isEmpty()) {
          allRequests.add(new CommunityPendingRequestDTO(
            community.getId(),
            community.getName(),
            pendingRequests
          ));
        }
      }

      return ResponseEntity.ok(new ApiResponse<>(200, "All pending requests fetched", allRequests));

    } catch (ResourceNotFoundException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
        new ApiResponse<>(404, ex.getMessage(), null)
      );
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(
        new ApiResponse<>(500, "An unexpected error occurred: " + e.getMessage(), null)
      );
    }
  }

}
