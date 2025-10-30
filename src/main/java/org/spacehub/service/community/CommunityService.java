package org.spacehub.service.community;

import org.spacehub.DTO.Community.CommunityMemberDTO;
import org.spacehub.DTO.Community.CommunityMemberRequest;
import org.spacehub.DTO.Community.DeleteCommunityDTO;
import org.spacehub.DTO.Community.JoinCommunity;
import org.spacehub.DTO.CancelJoinRequest;
import org.spacehub.DTO.AcceptRequest;
import org.spacehub.DTO.Community.LeaveCommunity;
import org.spacehub.DTO.RejectRequest;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.Community.CommunityUser;
import org.spacehub.DTO.Community.CommunityChangeRoleRequest;
import org.spacehub.entities.Community.Role;
import org.spacehub.entities.User.User;
import org.spacehub.repository.ChatRoom.ChatRoomRepository;
import org.spacehub.repository.community.CommunityRepository;
import org.spacehub.repository.UserRepository;
import org.spacehub.repository.community.CommunityUserRepository;
import org.spacehub.DTO.Community.CommunityBlockRequest;
import org.spacehub.DTO.Community.UpdateCommunityDTO;
import org.spacehub.service.S3Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import org.spacehub.DTO.Community.CreateRoomRequest;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Transactional
@Service
public class CommunityService {

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

    if (name == null || name.isBlank() || description == null || description.isBlank() || createdByEmail == null ||
      createdByEmail.isBlank()) {
      return ResponseEntity.badRequest()
        .body(new ApiResponse<>(400,
          "All fields (name, description, createdByEmail) are required", null));
    }

    if (imageFile == null || imageFile.isEmpty()) {
      return ResponseEntity.badRequest()
        .body(new ApiResponse<>(400, "Community image is required", null));
    }

    try {
      User creator = userRepository.findByEmail(createdByEmail).orElseThrow(() ->
        new RuntimeException("User not found with email: " + createdByEmail));

      validateImage(imageFile);

      String fileName = System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();
      String key = "communities/" + name.replaceAll("[^a-zA-Z0-9]", "_") + "/" + fileName;

      s3Service.uploadFile(key, imageFile.getInputStream(), imageFile.getSize());

      String imageUrl = s3Service.generatePresignedDownloadUrl(key, Duration.ofHours(2));

      Community community = new Community();
      community.setName(name);
      community.setDescription(description);
      community.setCreatedBy(creator);
      community.setImageUrl(key);
      community.setCreatedAt(LocalDateTime.now());

      Community savedCommunity = communityRepository.save(community);

      CommunityUser adminUser = new CommunityUser();
      adminUser.setCommunity(savedCommunity);
      adminUser.setUser(creator);
      adminUser.setRole(Role.ADMIN);
      adminUser.setJoinDate(LocalDateTime.now());
      adminUser.setBanned(false);
      communityUserRepository.save(adminUser);

      if (savedCommunity.getCommunityUsers() == null) {
        savedCommunity.setCommunityUsers(new HashSet<>());
      }
      savedCommunity.getCommunityUsers().add(adminUser);
      communityRepository.save(savedCommunity);

      Map<String, Object> responseData = new HashMap<>();
      responseData.put("communityId", savedCommunity.getId());
      responseData.put("name", savedCommunity.getName());
      responseData.put("imageUrl", imageUrl);

      return ResponseEntity.status(201)
        .body(new ApiResponse<>(201, "Community created successfully", responseData));
    } catch (IOException e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500,
        "Error uploading image: " + e.getMessage(), null));
    } catch (RuntimeException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: " +
        e.getMessage(), null));
    }
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
  public ResponseEntity<?> requestToJoinCommunity(@RequestBody JoinCommunity joinCommunity) {

    if (joinCommunity.getCommunityName() == null || joinCommunity.getCommunityName().isEmpty() ||
      joinCommunity.getUserEmail() == null || joinCommunity.getUserEmail().isEmpty()) {
      return ResponseEntity.badRequest().body("Check the fields");
    }

    Community community = communityRepository.findByName(joinCommunity.getCommunityName());

    if (community != null) {
      Optional<User> optionalUser = userRepository.findByEmail(joinCommunity.getUserEmail());

      if (optionalUser.isEmpty()) {
        return ResponseEntity.badRequest().body("User not found");
      }

      User user = optionalUser.get();

      boolean isAlreadyMember = community.getCommunityUsers().stream()
        .anyMatch(cu -> cu.getUser().getId().equals(user.getId()));

      if (isAlreadyMember) {
        return ResponseEntity.status(403).body("You are already in this community");
      }

      community.getPendingRequests().add(user);
      communityRepository.save(community);

      return ResponseEntity.ok().body("Request send to community");
    } else {
      return ResponseEntity.badRequest().body("Community not found");
    }
  }

  @CacheEvict(value = "communities", key = "#cancelJoinRequest.communityName")
  public ResponseEntity<?> cancelRequestCommunity(@RequestBody CancelJoinRequest cancelJoinRequest) {

    if (cancelJoinRequest.getCommunityName() != null && !cancelJoinRequest.getCommunityName().isEmpty() &&
      cancelJoinRequest.getUserEmail() != null && !cancelJoinRequest.getUserEmail().isEmpty()) {
      Community community = communityRepository.findByName(cancelJoinRequest.getCommunityName());

      if (community == null) {
        return ResponseEntity.badRequest().body("Community not found");
      }

      Optional<User> optionalUser = userRepository.findByEmail(cancelJoinRequest.getUserEmail());

      if (optionalUser.isEmpty()) {
        return ResponseEntity.badRequest().body("User not found");
      }

      User user = optionalUser.get();

      if (!community.getPendingRequests().contains(user)) {
        return ResponseEntity.status(403).body("No request found for this community");
      }

      community.getPendingRequests().remove(user);
      communityRepository.save(community);

      return ResponseEntity.ok().body("Cancelled the request to join the community");
    } else {
      return ResponseEntity.badRequest().body("Check the fields");
    }

  }

  @CacheEvict(value = "communities", key = "#acceptRequest.communityName")
  public ResponseEntity<?> acceptRequest(AcceptRequest acceptRequest) {

    if (isEmpty(acceptRequest.getUserEmail(), acceptRequest.getCommunityName(), acceptRequest.getCreatorEmail())) {
      return ResponseEntity.badRequest().body("Check the fields");
    }

    try {
      Community community = findCommunityByName(acceptRequest.getCommunityName());
      User creator = findUserByEmail(acceptRequest.getCreatorEmail());
      User user = findUserByEmail(acceptRequest.getUserEmail());

      if (!community.getCreatedBy().getId().equals(creator.getId())) {
        return ResponseEntity.status(403).body("You are not authorized to accept requests");
      }

      if (!community.getPendingRequests().contains(user)) {
        return ResponseEntity.badRequest().body("No pending request from this user");
      }

      community.getPendingRequests().remove(user);
      communityRepository.save(community);

      CommunityUser newMember = new CommunityUser();
      newMember.setCommunity(community);
      newMember.setUser(user);
      newMember.setRole(Role.MEMBER);
      newMember.setJoinDate(LocalDateTime.now());
      newMember.setBanned(false);
      communityUserRepository.save(newMember);

      return ResponseEntity.ok("User has been added to the community successfully");

    } catch (ResourceNotFoundException ex) {
      return ResponseEntity.badRequest().body(ex.getMessage());
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

    if (isEmpty(leaveCommunity.getCommunityName(), leaveCommunity.getUserEmail())) {
      return ResponseEntity.badRequest().body("Check the fields");
    }

    try {
      Community community = findCommunityByName(leaveCommunity.getCommunityName());
      User user = findUserByEmail(leaveCommunity.getUserEmail());

      if (community.getCreatedBy().getId().equals(user.getId())) {
        return ResponseEntity.status(403).body("Community creator cannot leave their own community");
      }

      Optional<CommunityUser> communityUserOptional = community.getCommunityUsers()
        .stream()
        .filter(cu -> cu.getUser().getId().equals(user.getId()))
        .findFirst();

      if (communityUserOptional.isEmpty()) {
        return ResponseEntity.badRequest().body("You are not a member of this community");
      }

      communityUserRepository.delete(communityUserOptional.get());
      return ResponseEntity.ok().body("You have left the community successfully");

    } catch (ResourceNotFoundException ex) {
      return ResponseEntity.badRequest().body(ex.getMessage());
    }
  }

  @CacheEvict(value = "communities", key = "#rejectRequest.communityName")
  public ResponseEntity<?> rejectRequest(RejectRequest rejectRequest) {

    if (rejectRequest.getCommunityName() != null && !rejectRequest.getCommunityName().isBlank() &&
      rejectRequest.getUserEmail() != null && !rejectRequest.getUserEmail().isBlank() &&
      !rejectRequest.getCreatorEmail().isBlank()) {
      Community community = communityRepository.findByName(rejectRequest.getCommunityName());
      if (community == null) return ResponseEntity.badRequest().body("Community not found");


      Optional<User> optionalCreator = userRepository.findByEmail(rejectRequest.getCreatorEmail());

      if (optionalCreator.isEmpty()) {
        return ResponseEntity.badRequest().body("Creator not found");
      }

      if (!community.getCreatedBy().getId().equals(optionalCreator.get().getId())) {
        return ResponseEntity.status(403).body("You are not authorized to reject requests");
      }

      Optional<User> optionalUser = userRepository.findByEmail(rejectRequest.getUserEmail());

      if (optionalUser.isEmpty()) {
        return ResponseEntity.badRequest().body("User not found");
      }

      User user = optionalUser.get();

      if (!community.getPendingRequests().contains(user)) {
        return ResponseEntity.badRequest().body("No pending request from this user");
      }

      community.getPendingRequests().remove(user);
      communityRepository.save(community);

      return ResponseEntity.ok().body("Join request rejected successfully");
    } else {
      return ResponseEntity.badRequest().body("Check the fields");
    }

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

  public ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityWithRooms(Long communityId) {

    Optional<Community> optionalCommunity = communityRepository.findById(communityId);
    if (optionalCommunity.isEmpty()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found",
        null));
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

    return ResponseEntity.ok(new ApiResponse<>(200, "Community details fetched successfully",
      response));
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
    if (request.getCommunityId() == null || request.getTargetUserEmail() == null ||
      request.getRequesterEmail() == null || request.getNewRole() == null) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields",
        null));
    }

    Community community = communityRepository.findById(request.getCommunityId()).orElse(null);
    if (community == null) return ResponseEntity.badRequest().body(new ApiResponse<>(400,
      "Community not found", null));

    Optional<User> optionalRequester = userRepository.findByEmail(request.getRequesterEmail());
    Optional<User> optionalTarget = userRepository.findByEmail(request.getTargetUserEmail());

    if (optionalRequester.isEmpty() || optionalTarget.isEmpty())
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));

    User requester = optionalRequester.get();
    User target = optionalTarget.get();

    if (!community.getCreatedBy().getId().equals(requester.getId())) {
      return ResponseEntity.status(403).body(new ApiResponse<>(403,
        "Only the community creator can change roles", null));
    }

    Optional<CommunityUser> communityUserOptional = community.getCommunityUsers()
      .stream()
      .filter(communityUser -> communityUser.getUser().getId().equals(target.getId()))
      .findFirst();

    if (communityUserOptional.isEmpty()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User is not a member",
        null));
    }

    CommunityUser communityUser = communityUserOptional.get();

    Role newRole;
    try {
      newRole = Role.valueOf(request.getNewRole().toUpperCase());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Invalid role: " +
        request.getNewRole(), null));
    }

    communityUser.setRole(newRole);
    communityUserRepository.save(communityUser);

    return ResponseEntity.ok(new ApiResponse<>(200, "Role of " + target.getEmail() +
      " changed to " + newRole, null));
  }

  public ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityMembers(Long communityId) {
    Optional<Community> optionalCommunity = communityRepository.findById(communityId);
    if (optionalCommunity.isEmpty()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found",
        null));
    }
    Community community = optionalCommunity.get();

    List<CommunityUser> communityUsers = communityUserRepository.findByCommunityId(communityId);

    List<CommunityMemberDTO> members = communityUsers.stream().map(communityUser -> {
      User user = communityUser.getUser();
      return CommunityMemberDTO.builder()
        .memberId(user.getId())
        .username(user.getUsername())
        .email(user.getEmail())
        .role(communityUser.getRole())
        .joinDate(communityUser.getJoinDate())
        .isBanned(communityUser.isBanned())
        .build();
    }).collect(Collectors.toList());

    Map<String, Object> response = new HashMap<>();
    response.put("communityId", community.getId());
    response.put("communityName", community.getName());
    response.put("totalMembers", members.size());
    response.put("members", members);

    return ResponseEntity.ok(new ApiResponse<>(200, "Community members fetched successfully",
      response));
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

  public ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> listAllCommunities() {
    List<Community> all = communityRepository.findAll();
    List<Map<String, Object>> out = all.stream().map(c -> {
      Map<String, Object> m = new HashMap<>();
      m.put("communityId", c.getId());
      m.put("name", c.getName());
      m.put("description", c.getDescription());
      m.put("imageUrl", c.getImageUrl());
      return m;
    }).toList();

    return ResponseEntity.ok(new ApiResponse<>(200, "Communities fetched", Map.of("communities",
      out)));
  }

  public ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityDetailsWithAdminFlag(Long communityId,
                                                                                           String requesterEmail) {
    Optional<Community> optionalCommunity = communityRepository.findById(communityId);
    if (optionalCommunity.isEmpty()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found",
        null));
    }
    Community community = optionalCommunity.get();

    boolean isAdmin = false;
    Optional<User> optionalUser = userRepository.findByEmail(requesterEmail);
    if (optionalUser.isPresent()) {
      User user = optionalUser.get();
      isAdmin = isUserAdminInCommunity(community, user);
    }

    List<ChatRoom> rooms = chatRoomRepository.findByCommunityId(communityId);

    Map<String, Object> response = new HashMap<>();
    response.put("communityId", community.getId());
    response.put("communityName", community.getName());
    response.put("description", community.getDescription());
    response.put("rooms", rooms);
    response.put("isAdmin", isAdmin);

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

      String code = UUID.randomUUID().toString().substring(0, 8);
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

  public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRoomsByCommunity(Long communityId) {
    Optional<Community> optionalCommunity = communityRepository.findById(communityId);
    if (optionalCommunity.isEmpty()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found",
        null));
    }

    List<ChatRoom> rooms = chatRoomRepository.findByCommunityId(communityId);

    List<Map<String, Object>> out = rooms.stream().map(r -> {
      Map<String, Object> m = new HashMap<>();
      m.put("id", r.getId());
      m.put("name", r.getName());
      m.put("roomCode", r.getRoomCode());
      return m;
    }).collect(Collectors.toList());

    return ResponseEntity.ok(new ApiResponse<>(200, "Rooms fetched successfully", out));
  }

  public ResponseEntity<?> deleteRoom(Long roomId, String requesterEmail) {
    try {
      ChatRoom room = chatRoomRepository.findById(roomId)
        .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + roomId));

      Community community = room.getCommunity();
      if (community == null) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400,
          "Associated community not found", null));
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

}
