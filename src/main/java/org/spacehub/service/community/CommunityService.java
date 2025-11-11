package org.spacehub.service.community;

import org.spacehub.DTO.Community.CommunityMemberDTO;
import org.spacehub.DTO.Community.CommunityMemberRequest;
import org.spacehub.DTO.Community.CommunityPendingRequestDTO;
import org.spacehub.DTO.Community.DeleteCommunityDTO;
import org.spacehub.DTO.Community.JoinCommunity;
import org.spacehub.DTO.Community.CancelJoinRequest;
import org.spacehub.DTO.Community.AcceptRequest;
import org.spacehub.DTO.Community.LeaveCommunity;
import org.spacehub.DTO.Community.RenameRoomRequest;
import org.spacehub.DTO.Community.RejectRequest;
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
import org.spacehub.repository.User.UserRepository;
import org.spacehub.repository.community.CommunityUserRepository;
import org.spacehub.DTO.Community.CommunityBlockRequest;
import org.spacehub.DTO.Community.UpdateCommunityDTO;
import org.spacehub.service.File.S3Service;
import org.spacehub.service.community.CommunityInterfaces.ICommunityService;
import org.spacehub.utils.ImageValidator;
import org.spacehub.utils.S3UrlHelper;
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
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.*;

import java.util.stream.Collectors;
import java.io.IOException;
import java.time.LocalDateTime;

@Transactional
@Service
public class CommunityService implements ICommunityService {

  private final CommunityRepository communityRepository;
  private final UserRepository userRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final CommunityUserRepository communityUserRepository;
  private final S3Service s3Service;
  private final S3UrlHelper s3UrlHelper;


  public static class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
      super(message);
    }
  }

  public CommunityService(CommunityRepository communityRepository, UserRepository userRepository,
                          ChatRoomRepository chatRoomRepository, S3Service s3Service,
                          CommunityUserRepository communityUserRepository, S3UrlHelper s3UrlHelper) {
    this.communityRepository = communityRepository;
    this.userRepository = userRepository;
    this.chatRoomRepository = chatRoomRepository;
    this.communityUserRepository = communityUserRepository;
    this.s3Service = s3Service;
    this.s3UrlHelper = s3UrlHelper;
  }

  public ResponseEntity<ApiResponse<Map<String, Object>>> createCommunity(
    String name, String description, String createdByEmail, MultipartFile imageFile) {

    try {
      validateCommunityInputs(name, description, createdByEmail, imageFile);

      User creator = getCreator(createdByEmail);
      ImageValidator.validate(imageFile);

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

  public ResponseEntity<ApiResponse<Void>> deleteCommunityByName(DeleteCommunityDTO deleteCommunity) {
    try {
      String validationError = validateDeleteCommunityRequest(deleteCommunity);
      if (validationError != null) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, validationError, null));
      }

      Community community = findCommunityByName(deleteCommunity.getName());
      User user = findUserByEmail(deleteCommunity.getUserEmail());

      if (!isCreatorOfCommunity(community, user)) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403,
          "You are not authorized to delete this community", null));
      }

      clearCommunityRelations(community);

      communityRepository.save(community);
      communityRepository.delete(community);

      return ok("Community deleted successfully");

    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    } catch (Exception e) {
      return ResponseEntity.internalServerError()
        .body(new ApiResponse<>(500, e.getMessage(), null));
    }
  }

  private String validateDeleteCommunityRequest(DeleteCommunityDTO dto) {
    if (dto == null) return "Request body cannot be null";
    if (dto.getName() == null || dto.getName().trim().isEmpty())
      return "Community name cannot be empty";
    if (dto.getUserEmail() == null || dto.getUserEmail().trim().isEmpty())
      return "User email cannot be empty";
    return null;
  }

  private void clearCommunityRelations(Community community) {
    if (community.getCommunityUsers() != null && !community.getCommunityUsers().isEmpty()) {
      communityUserRepository.deleteByCommunityId(community.getId());
      community.getCommunityUsers().clear();
    }

    if (community.getMembers() != null) {
      community.getMembers().clear();
    }

    if (community.getPendingRequests() != null) {
      community.getPendingRequests().clear();
    }
  }

  private ResponseEntity<ApiResponse<Void>> ok(String message) {
    return ResponseEntity.ok(new ApiResponse<>(200, message, null));
  }

  public ResponseEntity<ApiResponse<?>> requestToJoinCommunity(@RequestBody JoinCommunity joinCommunity) {
    try {

      if (joinCommunity == null) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Request body cannot be null", null));
      }
      if (joinCommunity.getCommunityName() == null || joinCommunity.getCommunityName().trim().isEmpty()) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community name is required", null));
      }
      if (joinCommunity.getUserEmail() == null || joinCommunity.getUserEmail().trim().isEmpty()) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User email is required", null));
      }

      Community community = findCommunityByName(joinCommunity.getCommunityName());
      User user = findUserByEmail(joinCommunity.getUserEmail());

      Optional<CommunityUser> existingMember = community.getCommunityUsers().stream()
              .filter(cu -> cu.getUser().getId().equals(user.getId())).findFirst();

      if (existingMember.isPresent()) {
        CommunityUser cu = existingMember.get();

        if (cu.isBlocked()) {
          return ResponseEntity.status(403).body(new ApiResponse<>(403, "You are blocked from this community", null));
        }

        if (cu.isBanned()) {
          return ResponseEntity.status(403).body(new ApiResponse<>(403, "You are banned from this community", null));
        }

        return ResponseEntity.status(409).body(new ApiResponse<>(409, "You are already a member of this community", null));
      }

      community.getPendingRequests().add(user);
      communityRepository.save(community);

      return ResponseEntity.ok().body(
        new ApiResponse<>(200, "Request send to community")
      );

    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    } catch (Exception e) {
      return ResponseEntity.internalServerError()
        .body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  public ResponseEntity<ApiResponse<?>> cancelRequestCommunity(@RequestBody CancelJoinRequest cancelJoinRequest) {
    try {
      Community community = findCommunityByName(cancelJoinRequest.getCommunityName());
      User user = findUserByEmail(cancelJoinRequest.getUserEmail());

      if (!community.getPendingRequests().contains(user)) {
        return ResponseEntity.status(403).body(
          new ApiResponse<>(403, "No join request found for this user in the community",
            null)
        );
      }

      if (community.getCommunityUsers().stream()
              .anyMatch(cu -> cu.getUser().getId().equals(user.getId()) && (cu.isBanned() ||
                cu.isBlocked()))) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403,
          "You are not allowed to cancel requests in this community", null));
      }

      community.getPendingRequests().remove(user);
      communityRepository.save(community);

      return ResponseEntity.ok(
        new ApiResponse<>(200, "Cancelled the join request successfully", null)
      );

    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    } catch (Exception e) {
      return ResponseEntity.internalServerError()
        .body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  public ResponseEntity<ApiResponse<?>> acceptRequest(AcceptRequest acceptRequest) {
    if (isInvalidRequest(acceptRequest)) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields",
        null));
    }

    try {
      String creatorEmail = acceptRequest.getCreatorEmail().trim().toLowerCase();
      String userEmail = acceptRequest.getUserEmail().trim().toLowerCase();
      String communityName = acceptRequest.getCommunityName().trim();

      Community community = findCommunityByName(communityName);
      User creator = findUserByEmail(creatorEmail);
      User user = findUserByEmail(userEmail);

      if (!hasPermissionToAccept(community, creator)) {
        return forbidden("Only Workspace Owner or Admins can accept requests");
      }

      if (hasPendingRequest(community, user)) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400,
          "No pending request from this user", null));
      }

      if (isUserMemberOfCommunity(creator, community)) {
        return forbidden("You are no longer an active member of this community");
      }

      approveRequest(community, user);

      return ResponseEntity.ok(new ApiResponse<>(200,
        "User has been added to the community successfully", null));

    } catch (ResourceNotFoundException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500,
        "Unexpected error: " + e.getMessage(), null));
    }
  }

  private boolean isInvalidRequest(AcceptRequest request) {
    return request == null ||
      isEmpty(request.getUserEmail(), request.getCommunityName(), request.getCreatorEmail());
  }

  private boolean hasPermissionToAccept(Community community, User creator) {
    boolean isAdmin = community.getCreatedBy().getId().equals(creator.getId());
    boolean isWorkspaceOwner = community.getCommunityUsers() != null &&
      community.getCommunityUsers().stream()
        .anyMatch(cu -> cu.getUser().getId().equals(creator.getId())
          && cu.getRole() == Role.WORKSPACE_OWNER);
    return isAdmin || isWorkspaceOwner;
  }

  private boolean hasPendingRequest(Community community, User user) {
    return community.getPendingRequests() == null ||
      community.getPendingRequests().stream()
        .noneMatch(u -> u != null && u.getId() != null && u.getId().equals(user.getId()));
  }

  private void approveRequest(Community community, User user) {
    community.getPendingRequests().removeIf(u -> u != null && u.getId() != null &&
      u.getId().equals(user.getId()));
    communityRepository.save(community);

    CommunityUser newMember = new CommunityUser();
    newMember.setCommunity(community);
    newMember.setUser(user);
    newMember.setRole(Role.MEMBER);
    newMember.setJoinDate(LocalDateTime.now());
    newMember.setBanned(false);
    newMember.setBlocked(false);
    communityUserRepository.save(newMember);

    if (community.getCommunityUsers() == null) {
      community.setCommunityUsers(new HashSet<>());
    }
    community.getCommunityUsers().add(newMember);
    communityRepository.save(community);
  }

  private ResponseEntity<ApiResponse<?>> forbidden(String message) {
    return ResponseEntity.status(403).body(new ApiResponse<>(403, message, null));
  }

  private boolean isEmpty(String... values) {
    for (String val : values) {
      if (val == null || val.isBlank()) return true;
    }
    return false;
  }

  public ResponseEntity<?> leaveCommunity(LeaveCommunity leaveCommunity) {
    if (isInvalidLeaveRequest(leaveCommunity)) {
      return badRequest("Check the fields");
    }

    try {
      Community community = findCommunityByName(leaveCommunity.getCommunityName());
      User user = findUserByEmail(leaveCommunity.getUserEmail());

      if (isCreatorOfCommunity(community, user)) {
        return ResponseEntity.status(403)
          .body(new ApiResponse<>(403,
            "Community creator cannot leave the community. Transfer ownership or " +
              "delete the community instead.", null));
      }

      Optional<CommunityUser> communityUserOptional = Optional.ofNullable(findCommunityUser(community, user));

      if (communityUserOptional.isEmpty()) {
        return badRequest("You are not a member of this community");
      }

      if (isUserMemberOfCommunity(user, community)) {
        return ResponseEntity.status(403)
                .body(new ApiResponse<>(403,
                  "You are not a member of this community or you have been removed", null));
      }

      removeCommunityUser(community, communityUserOptional.get(), user);
      communityRepository.save(community);

      return ResponseEntity.ok(new ApiResponse<>(200, "You have left the community successfully",
        null));

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

  public ResponseEntity<?> rejectRequest(RejectRequest rejectRequest) {
    ResponseEntity<ApiResponse<Object>> validationResponse = validateRejectRequest(rejectRequest);
    if (validationResponse != null) {
      return validationResponse;
    }

    try {
      String creatorEmail = sanitizeEmail(rejectRequest.getCreatorEmail());
      String userEmail = sanitizeEmail(rejectRequest.getUserEmail());
      String communityName = rejectRequest.getCommunityName().trim();

      Community community = findCommunityByName(communityName);
      User creator = findUserByEmail(creatorEmail);
      User user = findUserByEmail(userEmail);

      ResponseEntity<?> permissionResponse = checkPermission(creator, community);
      if (permissionResponse != null) {
        return permissionResponse;
      }

      if (hasPendingRequest(community, user)) {
        return badRequest("No pending request from this user");
      }

      removePendingRequest(community, user);
      return ok("Join request rejected successfully");

    } catch (Exception e) {
      return error("Failed to reject request: " + e.getMessage());
    }
  }

  private String sanitizeEmail(String email) {
    return email.trim().toLowerCase();
  }

  private ResponseEntity<?> checkPermission(User creator, Community community) {
    if (isUserMemberOfCommunity(creator, community)) {
      return forbidden("You are no longer a member of this community");
    }

    boolean isAdmin = community.getCreatedBy().getId().equals(creator.getId());
    boolean isWorkspaceOwner = community.getCommunityUsers() != null &&
      community.getCommunityUsers().stream()
        .anyMatch(cu -> cu.getUser().getId().equals(creator.getId())
          && cu.getRole() == Role.WORKSPACE_OWNER);

    if (!isWorkspaceOwner && !isAdmin) {
      return forbidden("Only Workspace Owner or Admins can reject requests");
    }

    return null;
  }

  private void removePendingRequest(Community community, User user) {
    community.getPendingRequests().removeIf(u -> u != null && u.getId() != null && u.getId().equals(user.getId()));
    communityRepository.save(community);
  }

  private ResponseEntity<ApiResponse<Object>> error(String message) {
    return ResponseEntity.status(500).body(new ApiResponse<>(500, message, null));
  }

  private ResponseEntity<ApiResponse<Object>> validateRejectRequest(RejectRequest req) {
    if (isBlank(req.getCommunityName()) || isBlank(req.getUserEmail()) || isBlank(req.getCreatorEmail())) {
      return badRequest("Check the fields");
    }
    return null;
  }

  private boolean isBlank(String s) {
    return s == null || s.isBlank();
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

  public ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityWithRooms(UUID communityId,
                                                                                String userEmail) {
    Optional<Community> optionalCommunity = communityRepository.findById(communityId);
    if (optionalCommunity.isEmpty()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400,
       "Community not found", null));
    }

    Community community = optionalCommunity.get();
    User user = findUserByEmail(userEmail);

    if (isUserMemberOfCommunity(user, community)) {
      return ResponseEntity.status(403).body(new ApiResponse<>(403,
        "Access denied: You are not a member of this community", null));
    }

    List<ChatRoom> rooms = chatRoomRepository.findByCommunityId(communityId);
    Map<String, Object> response = buildCommunityResponse(community, rooms);
    return ResponseEntity.ok(new ApiResponse<>(200, "Community details fetched successfully",
      response));
  }

  private Map<String, Object> buildCommunityResponse(Community community, List<ChatRoom> rooms) {
    Map<String, Object> response = new HashMap<>();

    response.put("communityId", community.getId());
    response.put("communityName", community.getName());
    response.put("description", community.getDescription());
    response.put("rooms", rooms);

    List<Map<String, Object>> members = new ArrayList<>();
    for (CommunityUser communityUser : community.getCommunityUsers()) {
      Map<String, Object> memberData = new HashMap<>();

      User user = communityUser.getUser();

      memberData.put("email", user != null ? user.getEmail() : null);
      memberData.put("username", user != null ? user.getUsername() : null);
      memberData.put("role", communityUser.getRole() != null ? communityUser.getRole().toString() : "MEMBER");

      members.add(memberData);
    }

    response.put("members", members);
    return response;
  }

  public ResponseEntity<ApiResponse<String>> removeMemberFromCommunity(CommunityMemberRequest request) {
    try {
      Community community = communityRepository.findById(request.getCommunityId()).orElseThrow(() ->
        new ResourceNotFoundException("Community not found"));

      User requester = findUserByEmail(request.getRequesterEmail());
      User target = findUserByEmail(request.getUserEmail());

      if (!isUserMemberOfCommunity(requester, community)) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403,
          "You are not a member of this community", null));
      }

      if (!isUserMemberOfCommunity(target, community)) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400,
          "Target user is not a member of this community", null));
      }

      Role requesterRole = getUserRoleInCommunity(community, requester);
      Role targetRole = getUserRoleInCommunity(community, target);

      if (!canRequesterRemoveTarget(requesterRole, targetRole)) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403,
          "You do not have permission to remove this member", null));
      }

      CommunityUser communityUser = communityUserRepository
        .findByCommunityIdAndUserId(community.getId(), target.getId())
        .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this community"));

      if (community.getMembers().contains(target)) {
        community.getMembers().remove(target);
        communityRepository.save(community);
      }

      communityUserRepository.delete(communityUser);

      return ResponseEntity.ok(new ApiResponse<>(200, "Member removed successfully", null));

    } catch (ResourceNotFoundException ex) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, ex.getMessage(), null));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500,
        "Unexpected error: " + e.getMessage(), null));
    }
  }

  private boolean canRequesterRemoveTarget(Role requesterRole, Role targetRole) {
    return switch (requesterRole) {
      case ADMIN ->
        targetRole == Role.MEMBER || targetRole == Role.WORKSPACE_OWNER;
      case WORKSPACE_OWNER ->
        targetRole == Role.MEMBER;
      default ->
        false;
    };
  }

  private Role getUserRoleInCommunity(Community community, User user) {
    return community.getCommunityUsers().stream().filter(cu -> cu.getUser().getId().equals(user.getId()))
            .map(CommunityUser::getRole).findFirst().orElse(Role.MEMBER);
  }

  public ResponseEntity<ApiResponse<String>> changeMemberRole(CommunityChangeRoleRequest request) {
    try {
      validateChangeRoleRequest(request);

      Community community = findCommunity(request.getCommunityId());
      User requester = findUser(request.getRequesterEmail(), "Requester not found");
      User target = findUser(request.getTargetUserEmail(), "Target user not found");

      ResponseEntity<ApiResponse<String>> membershipValidation =
        validateMembership(requester, target, community);
      if (membershipValidation != null) {
        return membershipValidation;
      }

      Role requesterRole = getUserRoleInCommunity(community, requester);
      Role targetRole = getUserRoleInCommunity(community, target);
      Role newRole = parseRole(request.getNewRole());

      verifyCreatorPermission(community, requester);

      if (!canChangeRole(requesterRole, targetRole, newRole)) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403,
          "You do not have permission to change this user's role", null));
      }

      if (targetRole == newRole) {
        return ResponseEntity.ok(new ApiResponse<>(200, "User already has this role", null));
      }

      CommunityUser communityUser = findCommunityUser(community, target);
      communityUser.setRole(newRole);
      communityUserRepository.save(communityUser);

      return ResponseEntity.ok(new ApiResponse<>(200, "Role of " + target.getEmail() +
        " changed to " + newRole, null));
    }
    catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    }
    catch (SecurityException e) {
      return ResponseEntity.status(403).body(new ApiResponse<>(403, e.getMessage(), null));
    }
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500,
        "Unexpected error: " + e.getMessage(), null));
    }
  }

  private ResponseEntity<ApiResponse<String>> validateMembership(User requester, User target, Community community) {
    if (isUserMemberOfCommunity(requester, community)) {
      return ResponseEntity.status(403).body(new ApiResponse<>(403,
        "You are not a member of this community", null));
    }

    if (isUserMemberOfCommunity(target, community)) {
      return ResponseEntity.status(403).body(new ApiResponse<>(403,
        "Target user is not a member of this community", null));
    }

    return null;
  }

  private boolean canChangeRole(Role requesterRole, Role targetRole, Role newRole) {
    return switch (requesterRole) {
      case ADMIN -> true;
      case WORKSPACE_OWNER -> (targetRole == Role.MEMBER && newRole == Role.WORKSPACE_OWNER);
      default -> false;
    };
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
      .filter(cu -> !cu.isBanned())
      .map(communityUser -> {
        User user = communityUser.getUser();
        return CommunityMemberDTO.builder()
          .memberId(user.getId())
          .username(user.getUsername())
          .email(user.getEmail())
          .role(communityUser.getRole())
          .joinDate(communityUser.getJoinDate())
          .isBanned(communityUser.isBanned())
          .avatarPreviewUrl(generatePresignedUrlSafely(user.getAvatarUrl()))
          .bio(user.getBio())
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

    try {
      Community community = communityRepository.findById(request.getCommunityId())
        .orElseThrow(() -> new ResourceNotFoundException("Community not found"));

      User requester = userRepository.findByEmail(request.getRequesterEmail())
        .orElseThrow(() -> new ResourceNotFoundException("Requester not found"));

      User target = userRepository.findByEmail(request.getTargetUserEmail())
        .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));

      if (!isUserMemberOfCommunity(requester, community)) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403,
          "You are not a member of this community", null));
      }

      if (!isUserMemberOfCommunity(target, community)) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400,
          "Target user is not a member of this community", null));
      }

      Role requesterRole = getUserRoleInCommunity(community, requester);
      Role targetRole = getUserRoleInCommunity(community, target);

      if (!canRequesterBlockTarget(requesterRole, targetRole)) {
        return ResponseEntity.status(403)
          .body(new ApiResponse<>(403, "You do not have permission to block or unblock this user",
            null));
      }

      CommunityUser communityUser = communityUserRepository
        .findByCommunityIdAndUserId(community.getId(), target.getId())
        .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this community"));

      communityUser.setBanned(request.isBlock());
      communityUserRepository.save(communityUser);

      String blocked = request.isBlock() ? "blocked" : "unblocked";
      return ResponseEntity.ok(new ApiResponse<>(200, "User " + target.getEmail() + " has been " +
        blocked + " successfully", null));

    } catch (ResourceNotFoundException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: " +
        e.getMessage(), null));
    }
  }

  private boolean canRequesterBlockTarget(Role requesterRole, Role targetRole) {
    return switch (requesterRole) {
      case ADMIN -> true;
      case WORKSPACE_OWNER -> targetRole == Role.MEMBER;
      default -> false;
    };
  }

  public ResponseEntity<ApiResponse<Community>> updateCommunityInfo(UpdateCommunityDTO dto) {
    ResponseEntity<ApiResponse<Community>> validationResponse = validateUpdateCommunityRequest(dto);
    if (validationResponse != null) {
      return validationResponse;
    }

    Community community = findCommunity(dto.getCommunityId());
    if (community == null) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found",
        null));
    }

    User requester = findUser(dto.getRequesterEmail());
    if (requester == null) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Requester not found",
        null));
    }

    if (isUserMemberOfCommunity(requester, community)) {
      return ResponseEntity.status(403).body(new ApiResponse<>(403,
        "You are not a member of this community", null));
    }

    if (!canUpdateCommunity(community, requester)) {
      return ResponseEntity.status(403).body(new ApiResponse<>(403,
        "Only admins or workspace owners can update community info", null));
    }

    applyCommunityUpdates(community, dto);
    communityRepository.save(community);

    return ResponseEntity.ok(new ApiResponse<>(200, "Community info updated successfully",
      community));
  }

  private ResponseEntity<ApiResponse<Community>> validateUpdateCommunityRequest(UpdateCommunityDTO dto) {
    if (dto == null || dto.getCommunityId() == null || dto.getRequesterEmail() == null) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400,
        "Invalid request: Missing required fields", null));
    }
    return null;
  }

  private User findUser(String email) {
    return userRepository.findByEmail(email).orElse(null);
  }

  private boolean canUpdateCommunity(Community community, User requester) {
    Role requesterRole = getUserRoleInCommunity(community, requester);
    return community.getCreatedBy().getId().equals(requester.getId()) ||
      requesterRole == Role.WORKSPACE_OWNER ||
      requesterRole == Role.ADMIN;
  }

  private void applyCommunityUpdates(Community community, UpdateCommunityDTO dto) {
    if (dto.getName() != null && !dto.getName().isBlank()) {
      community.setName(dto.getName());
    }
    if (dto.getDescription() != null && !dto.getDescription().isBlank()) {
      community.setDescription(dto.getDescription());
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

  @Override
  public ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> listAllCommunities() {

    List<Community> all = communityRepository.findAll();

    List<Map<String, Object>> out = all.stream()
            .map(this::buildCommunityBasicInfo).toList();

    return ResponseEntity.ok(new ApiResponse<>(200, "Communities fetched successfully", Map.of("communities", out)));
  }

  public ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> listMyCommunities(String requesterEmail) {
    try {
      if (isInvalidEmail(requesterEmail)) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Requester email is required"));
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

      for (Map<String, Object> community : userCommunities) {
        UUID communityId = (UUID) community.get("communityId");
        long memberCount = communityUserRepository.countByCommunityId(communityId);
        community.put("memberCount", memberCount);
      }

      return ResponseEntity.ok(
              new ApiResponse<>(200, "User's communities fetched with member counts",
                      Map.of("communities", userCommunities))
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

  private List<Map<String, Object>> buildCommunityListForUser(String normalizedEmail) {
    Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
    if (userOpt.isEmpty()) {
      return Collections.emptyList();
    }

    User user = userOpt.get();
    List<Community> all = communityRepository.findAll();
    List<Map<String, Object>> out = new ArrayList<>();

    for (Community c : all) {
      if (!isUserInCommunity(c, user)) {
        continue;
      }
      out.add(buildCommunityMap(c, user));
    }

    return out;
  }

  private boolean isUserInCommunity(Community c, User user) {
    boolean isCreator = c.getCreatedBy() != null && c.getCreatedBy().getId().equals(user.getId());
    if (isCreator) return true;

    List<CommunityUser> members = communityUserRepository.findByCommunityId(c.getId());
    return members.stream()
      .anyMatch(cu -> cu.getUser() != null && cu.getUser().getId().equals(user.getId()));
  }

  private Map<String, Object> buildCommunityMap(Community c, User user) {
    Map<String, Object> m = new HashMap<>();

    m.put("communityId", c.getId());
    m.put("name", c.getName());
    m.put("description", c.getDescription());
    m.put("role", getUserRole(c, user));

    setImageInfo(m, "image", c.getImageUrl());
    setImageInfo(m, "banner", c.getBannerUrl());

    return m;
  }

  private String getUserRole(Community c, User user) {
    if (c.getCreatedBy() != null && c.getCreatedBy().getId().equals(user.getId())) {
      return "ADMIN";
    }

    return communityUserRepository.findByCommunityId(c.getId()).stream()
      .filter(cu -> cu.getUser() != null && cu.getUser().getId().equals(user.getId()))
      .map(cu -> cu.getRole().name())
      .findFirst()
      .orElse("MEMBER");
  }

  private void setImageInfo(Map<String, Object> map, String type, String key) {
    String urlKey = type + "Url";
    String keyName = type + "Key";

    if (key == null || key.isBlank()) {
      map.put(urlKey, null);
      map.put(keyName, null);
      return;
    }

    try {
      String presigned = s3Service.generatePresignedDownloadUrl(key, Duration.ofHours(1));
      map.put(urlKey, presigned);
    } catch (Exception e) {
      map.put(urlKey, null);
    }
    map.put(keyName, key);
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
    try {
      if (communityId == null || requesterEmail == null || requesterEmail.isBlank()) {
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(400, "Community ID and requester email are required",
                  null));
      }

      Optional<Community> optionalCommunity = communityRepository.findById(communityId);
      if (optionalCommunity.isEmpty()) {
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(400, "Community not found", null));
      }

      Community community = optionalCommunity.get();

      Optional<User> optionalUser = userRepository.findByEmail(requesterEmail.trim().toLowerCase());
      if (optionalUser.isEmpty()) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Requester not found",
          null));
      }
      User requester = optionalUser.get();

      Optional<CommunityUser> cuOpt = community.getCommunityUsers().stream()
              .filter(cu -> cu.getUser().getId().equals(requester.getId())).findFirst();

      if (cuOpt.isEmpty()) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403,
          "You are not a member of this community", null));
      }

      CommunityUser communityUser = cuOpt.get();

      if (communityUser.isBanned()) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403,
          "You are banned from this community", null));
      }
      Role role = communityUser.getRole();
      boolean isAdmin = role == Role.ADMIN;
      boolean isWorkspaceOwner = role == Role.WORKSPACE_OWNER;
      boolean isCreator = community.getCreatedBy() != null &&
        community.getCreatedBy().getId().equals(requester.getId());

      List<ChatRoom> rooms = chatRoomRepository.findByCommunityId(communityId);

      Map<String, Object> response = buildCommunityResponse(community, rooms);

      response.put("role", role.toString());
      response.put("isAdmin", isAdmin);
      response.put("isWorkspaceOwner", isWorkspaceOwner);
      response.put("isCreator", isCreator);

      return ResponseEntity.ok(
              new ApiResponse<>(200, "Community details fetched successfully", response)
      );
    }
    catch (Exception e){
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  private boolean isUserAdminInCommunity(Community community, User user) {
    if (community.getCreatedBy() != null && community.getCreatedBy().getId().equals(user.getId())) {
      return false;
    }
    return community.getCommunityUsers().stream()
      .noneMatch(cu -> cu.getUser().getId().equals(user.getId()) && cu.getRole() == Role.ADMIN);
  }

  public ResponseEntity<?> createRoomInCommunity(CreateRoomRequest request) {
    try {
      ResponseEntity<?> validationResponse = validateCreateRoomRequest(request);
      if (validationResponse != null) {
        return validationResponse;
      }

      Community community = findCommunityById(request.getCommunityId());
      User requester = findUserByEmail(request.getRequesterEmail());

      CommunityUser communityUser = findCommunityUser(community, requester);
      if (communityUser == null) {
        return forbidden("You are not a member of this community");
      }

      if (!canCreateRoom(community, requester, communityUser.getRole())) {
        return forbidden("Only admins or workspace owners can create rooms");
      }

      ResponseEntity<?> roomNameResponse = validateRoomName(request, community);
      if (roomNameResponse != null) {
        return roomNameResponse;
      }

      ChatRoom newRoom = buildNewRoom(request.getRoomName(), community);
      ChatRoom savedRoom = chatRoomRepository.save(newRoom);

      return ResponseEntity.status(201)
        .body(new ApiResponse<>(201, "Room created successfully", savedRoom));

    } catch (ResourceNotFoundException e) {
      return badRequest(e.getMessage());
    } catch (Exception e) {
      return serverError("An unexpected error occurred: " + e.getMessage());
    }
  }

  private ResponseEntity<?> validateCreateRoomRequest(CreateRoomRequest request) {
    if (request == null ||
      request.getRequesterEmail() == null || request.getRequesterEmail().isBlank() ||
      request.getCommunityId() == null) {
      return badRequest("Community ID and requester email are required");
    }
    return null;
  }

  private Community findCommunityById(UUID communityId) {
    return communityRepository.findById(communityId)
      .orElseThrow(() -> new ResourceNotFoundException("Community not found with ID: " + communityId));
  }

  private boolean canCreateRoom(Community community, User requester, Role role) {
    return role == Role.ADMIN || role == Role.WORKSPACE_OWNER ||
      (community.getCreatedBy() != null && community.getCreatedBy().getId().equals(requester.getId()));
  }

  private ResponseEntity<?> validateRoomName(CreateRoomRequest request, Community community) {
    if (request.getRoomName() == null || request.getRoomName().isBlank()) {
      return badRequest("Room name cannot be empty");
    }

    boolean exists = chatRoomRepository.findByCommunityId(community.getId()).stream()
      .anyMatch(room -> room.getName().equalsIgnoreCase(request.getRoomName().trim()));

    if (exists) {
      return badRequest("A room with this name already exists");
    }
    return null;
  }

  private ChatRoom buildNewRoom(String roomName, Community community) {
    ChatRoom room = new ChatRoom();
    room.setName(roomName.trim());
    room.setCommunity(community);
    room.setRoomCode(UUID.randomUUID());
    return room;
  }

  @Override
  public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRoomsByCommunity(UUID communityId) {
    try {
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
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  @Override
  public ResponseEntity<?> deleteRoom(UUID communityId, UUID roomId, String requesterEmail) {
    try {
      String validationError = validateDeleteRoomInput(communityId, roomId, requesterEmail);
      if (validationError != null) {
        return badRequest(validationError);
      }

      ChatRoom room = getChatRoom(roomId);
      Community community = verifyRoomCommunity(room, communityId);
      User requester = getUserByEmail(requesterEmail);

      CommunityUser communityUser = getCommunityUser(community, requester);
      verifyDeletePermission(community, requester, communityUser);

      chatRoomRepository.delete(room);
      return ok("Room deleted successfully");

    } catch (ResourceNotFoundException e) {
      return badRequest(e.getMessage());
    } catch (Exception e) {
      return serverError("Unexpected error: " + e.getMessage());
    }
  }

  private String validateDeleteRoomInput(UUID communityId, UUID roomId, String requesterEmail) {
    if (communityId == null || roomId == null || requesterEmail == null || requesterEmail.isBlank()) {
      return "Community ID, Room ID, and requester email are required";
    }
    return null;
  }

  private ChatRoom getChatRoom(UUID roomId) {
    return chatRoomRepository.findById(roomId)
      .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: " + roomId));
  }

  private Community verifyRoomCommunity(ChatRoom room, UUID communityId) {
    Community community = room.getCommunity();
    if (community == null || !community.getId().equals(communityId)) {
      throw new ResourceNotFoundException("Room does not belong to the specified community");
    }
    return community;
  }

  private User getUserByEmail(String email) {
    return userRepository.findByEmail(email.trim().toLowerCase())
      .orElseThrow(() -> new ResourceNotFoundException("Requester not found with email: " + email));
  }

  private CommunityUser getCommunityUser(Community community, User requester) {
    return communityUserRepository.findByCommunityIdAndUserId(community.getId(), requester.getId())
      .orElseThrow(() -> new SecurityException("You are not a member of this community"));
  }

  private void verifyDeletePermission(Community community, User requester, CommunityUser communityUser) {
    Role role = communityUser.getRole();
    boolean canDelete = role == Role.ADMIN || role == Role.WORKSPACE_OWNER;

    if (community.getCreatedBy() != null && community.getCreatedBy().getId().equals(requester.getId())) {
      canDelete = true;
    }

    if (!canDelete) {
      throw new SecurityException("Only admins or workspace owners can delete rooms");
    }
  }

  public ResponseEntity<?> searchCommunities(String q, String requesterEmail, int page, int size) {
    if (q == null || q.isBlank()) {
      return listAllCommunities();
    }

    Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));

    String pattern = Arrays.stream(q.split("")).filter(ch -> !ch.isBlank())
            .collect(Collectors.joining("%"));

    Page<Community> communityPage = communityRepository.searchByNamePattern(pattern, pageable);

    User requester = null;
    if (requesterEmail != null && !requesterEmail.isBlank()) {
      requester = userRepository.findByEmail(requesterEmail).orElse(null);
    }
    final User finalRequester = requester;

    List<Map<String, Object>> results = communityPage.getContent().stream().map(c -> {
      Map<String, Object> m = buildCommunityBasicInfo(c);

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

    Map<String, Object> body = buildPagedResponse(communityPage, results);
    return ResponseEntity.ok(new ApiResponse<>(200, "Search results", body));
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

  private Map<String, Object> buildCommunityBasicInfo(Community c) {
    Map<String, Object> m = new HashMap<>();

    m.put("communityId", c.getId());
    m.put("name", c.getName());
    m.put("description", c.getDescription());

    try {
      Map<String, Object> img = s3UrlHelper.generatePresignedUrl(c.getImageUrl(), Duration.ofHours(1));
      m.put("imageUrl", img.get("url"));
      m.put("imageKey", img.get("key"));
    }
    catch (Exception e) {
      m.put("imageUrl", null);
      m.put("imageKey", null);
    }

    String bannerKey = c.getBannerUrl();
    if (bannerKey != null && !bannerKey.isBlank()) {
      try {
        String presigned = s3Service.generatePresignedDownloadUrl(bannerKey, Duration.ofHours(1));
        m.put("bannerUrl", presigned);
      }
      catch (Exception e) {
        m.put("bannerUrl", null);
      }
    }
    else {
      m.put("bannerUrl", null);
    }

    if (c.getCreatedBy() != null) {
      m.put("createdBy", c.getCreatedBy().getUsername());
    }

    if (c.getCommunityUsers() != null) {
      long memberCount = c.getCommunityUsers().stream().filter(u -> !u.isBanned()).count();
      m.put("totalMembers", memberCount);
    }
    else {
      m.put("totalMembers", 0);
    }

    return m;
  }

  @Override
  public ResponseEntity<?> enterOrRequestCommunity(UUID communityId, String requesterEmail) {
    if (requesterEmail == null || requesterEmail.isBlank()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "requesterEmail is required",
        null));
    }

    try {
      Community community = getCommunityOrThrow(communityId);
      User user = getUserOrThrow(requesterEmail.trim().toLowerCase());

      boolean isBanned = community.getCommunityUsers().stream()
              .anyMatch(cu -> cu.getUser().getId().equals(user.getId()) && cu.isBanned());
      if (isBanned) {
        return ResponseEntity.status(403)
                .body(new ApiResponse<>(403, "You are banned from this community", Map.of("accessDenied", true)));
      }

      boolean isMember = community.getCommunityUsers().stream()
              .anyMatch(cu -> cu.getUser().getId().equals(user.getId()));

      if (isMember) {
        return getCommunityWithRooms(communityId, requesterEmail);
      }

      boolean isPending = community.getPendingRequests().stream()
              .anyMatch(u -> u.getId().equals(user.getId()));

      if (isPending) {
        return ResponseEntity.ok(
                new ApiResponse<>(200, "Join request already pending",
                  Map.of("requested", true, "message", "Join request already pending")));
      }

      community.getPendingRequests().add(user);
      communityRepository.save(community);

      return ResponseEntity.ok(
              new ApiResponse<>(200, "Join request sent successfully",
                Map.of("requested", true, "message", "Join request sent successfully")));

    }
    catch (ResourceNotFoundException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    }
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500,
        "Unexpected error: " + e.getMessage(), null));
    }
  }

  @Override
  public ResponseEntity<?> uploadCommunityAvatar(UUID communityId, String requesterEmail,
                                                 MultipartFile imageFile) {
    if (requesterEmail == null || requesterEmail.isBlank()) {
      return badRequest("requesterEmail is required");
    }

    if (imageFile == null || imageFile.isEmpty()) {
      return badRequest("Image file is required");
    }

    try {
      Community community = getCommunityOrThrow(communityId);
      User requester = getUserOrThrow(requesterEmail);

      if (isUserAdminInCommunity(community, requester)) {
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
    }
    catch (IOException e) {
      return serverError("Error uploading image: " + e.getMessage());
    }
    catch (ResourceNotFoundException e) {
      return badRequest(e.getMessage());
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

      if (isUserAdminInCommunity(community, requester)) {
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
    if (file == null || file.isEmpty()) {
      return;
    }

    String avatarKey = uploadAndReturnKey(file, "communities/" + safeName + "/avatar/");
    community.setImageUrl(avatarKey);
    body.put("communityAvatarKey", avatarKey);
    body.put("communityAvatarUrl", tryGeneratePresigned(avatarKey));
  }

  private void uploadCommunityBanner(Community community, MultipartFile file, String safeName,
                                     Map<String, Object> body) throws IOException {
    if (file == null || file.isEmpty()) {
      return;
    }

    String bannerKey = uploadAndReturnKey(file, "communities/" + safeName + "/banner/");
    community.setBannerUrl(bannerKey);
    body.put("bannerKey", bannerKey);
    body.put("bannerUrl", tryGeneratePresigned(bannerKey));
  }

  private void uploadUserAvatar(User requester, MultipartFile file, Map<String, Object> body) throws IOException {
    if (file == null || file.isEmpty()) {
      return;
    }

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
    return ResponseEntity.status(403).body(new ApiResponse<>(403,
      "Only community admin can change banner/info", null));
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
    ResponseEntity<ApiResponse<Object>> inputErr = validateRenameRoomInput(communityId, roomId, req);
    if (inputErr != null) return inputErr;

    try {
      Community community = getCommunityOrThrow(communityId);
      User requester = getUserOrThrow(req.getRequesterEmail());

      CommunityUser communityUser = communityUserRepository
        .findByCommunityIdAndUserId(community.getId(), requester.getId())
        .orElse(null);
      if (communityUser == null) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403,
          "You are not a member of this community", null));
      }

      if (!canRenameRoom(community, requester, communityUser.getRole())) {
        return ResponseEntity.status(403)
          .body(new ApiResponse<>(403, "Only admins or workspace owners can rename rooms", null));
      }

      ChatRoom room = getChatRoomOrThrow(roomId);

      if (room.getCommunity() == null || !room.getCommunity().getId().equals(communityId)) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400,
          "Room doesn't belong to the specified community", null));
      }

      String newRoomName = req.getNewRoomName().trim();
      boolean nameExists = chatRoomRepository.findByCommunityId(communityId).stream()
        .anyMatch(r -> r.getName().equalsIgnoreCase(newRoomName) && !r.getId().equals(roomId));

      if (nameExists) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400,
          "Another room with this name already exists", null));
      }

      room.setName(newRoomName);
      chatRoomRepository.save(room);

      return ResponseEntity.ok(
        new ApiResponse<>(200, "Room renamed successfully", Map.of(
          "roomId", room.getId(),
          "newName", room.getName(),
          "communityId", community.getId()
        ))
      );

    } catch (ResourceNotFoundException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  private ResponseEntity<ApiResponse<Object>> validateRenameRoomInput(UUID communityId, UUID roomId, RenameRoomRequest req) {
    if (communityId == null || roomId == null) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400,
        "Community ID and Room ID are required", null));
    }
    if (req == null || req.getRequesterEmail() == null || req.getRequesterEmail().isBlank()
      || req.getNewRoomName() == null || req.getNewRoomName().isBlank()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400,
        "Requester email and new room name are required", null));
    }
    return null;
  }

  private ChatRoom getChatRoomOrThrow(UUID roomId) {
    return chatRoomRepository.findById(roomId)
      .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
  }

  private boolean canRenameRoom(Community community, User requester, Role requesterRole) {
    if (requesterRole == Role.ADMIN || requesterRole == Role.WORKSPACE_OWNER) return true;
    return (community.getCreatedBy() != null && community.getCreatedBy().getId().equals(requester.getId()));
  }

  @Override
  public ResponseEntity<?> getRolesForRequester(UUID communityId, String requesterEmail) {
    if (communityId == null || requesterEmail == null || requesterEmail.isBlank()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400,
        "communityId and requesterEmail are required", null));
    }

    try {
      Community community = communityRepository.findById(communityId).orElseThrow(() ->
        new ResourceNotFoundException("Community not found"));

      User requester = userRepository.findByEmail(requesterEmail.trim().toLowerCase())
              .orElseThrow(() -> new ResourceNotFoundException("User not found"));

      Optional<CommunityUser> cuOpt = communityUserRepository.findByCommunityIdAndUserId(
              community.getId(), requester.getId());

      if (cuOpt.isEmpty()) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403,
          "You are not a member of this community", null));
      }

      CommunityUser communityUser = cuOpt.get();
      Role role = communityUser.getRole();

      boolean isAdmin = role == Role.ADMIN;
      boolean isWorkspaceOwner = role == Role.WORKSPACE_OWNER;
      boolean isMember = role == Role.MEMBER;
      boolean isCreator = community.getCreatedBy() != null &&
        community.getCreatedBy().getId().equals(requester.getId());

      Map<String, Object> roleInfo = new HashMap<>();
      roleInfo.put("role", role.toString());
      roleInfo.put("isAdmin", isAdmin);
      roleInfo.put("isWorkspaceOwner", isWorkspaceOwner);
      roleInfo.put("isMember", isMember);
      roleInfo.put("isCreator", isCreator);

      return ResponseEntity.ok(
              new ApiResponse<>(200, "Role details fetched successfully", roleInfo));

    }
    catch (ResourceNotFoundException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    }
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  public ResponseEntity<ApiResponse<Map<String, Object>>> discoverCommunities(String currentUserEmail, int page,
                                                                              int size) {
    Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
    Page<Community> communityPage = communityRepository.findAll(pageable);

    User currentUser = userRepository.findByEmail(currentUserEmail).orElseThrow(() ->
      new UsernameNotFoundException("User not found with email: " + currentUserEmail));

    List<Map<String, Object>> out = communityPage.getContent().stream().map(c -> {
      Map<String, Object> m = new HashMap<>();
      m.put("communityId", c.getId());
      m.put("name", c.getName());
      m.put("description", c.getDescription());

      String bannerKey = c.getBannerUrl();
      if (bannerKey != null && !bannerKey.isBlank()) {
        try {
          String presignedBanner = s3Service.generatePresignedDownloadUrl(bannerKey, Duration.ofHours(1));
          m.put("bannerUrl", presignedBanner);
          m.put("bannerKey", bannerKey);
        }
        catch (Exception e) {
          m.put("bannerUrl", null);
          m.put("bannerKey", bannerKey);
        }
      }
      else {
        m.put("bannerUrl", null);
        m.put("bannerKey", null);
      }

      String imageKey = c.getImageUrl();
      if (imageKey != null && !imageKey.isBlank()) {
        try {
          String presignedImage = s3Service.generatePresignedDownloadUrl(imageKey, Duration.ofHours(1));
          m.put("imageUrl", presignedImage);
          m.put("imageKey", imageKey);
        }
        catch (Exception e) {
          m.put("imageUrl", null);
          m.put("imageKey", imageKey);
        }
      }
      else {
        m.put("imageUrl", null);
        m.put("imageKey", null);
      }

      if (c.getCreatedBy() != null) {
        m.put("createdBy", c.getCreatedBy().getEmail());
      }
      m.put("createdAt", c.getCreatedAt());

      boolean joined = false;
      String role = null;
      boolean isBanned = false;

      Optional<CommunityUser> membership =
              communityUserRepository.findByCommunityIdAndUserId(c.getId(), currentUser.getId());

      if (membership.isPresent()) {
        joined = true;
        CommunityUser cu = membership.get();
        role = cu.getRole() != null ? cu.getRole().name() : null;
        isBanned = cu.isBanned();
      }

      m.put("joined", joined);
      m.put("role", role);
      m.put("isBanned", isBanned);

      long memberCount = communityUserRepository.countByCommunityId(c.getId());
      m.put("memberCount", memberCount);

      return m;
    }).collect(Collectors.toList());

    Map<String, Object> body = buildPagedResponse(communityPage, out);
    return ResponseEntity.ok(new ApiResponse<>(200, "Discover communities fetched successfully",
      body));
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

      User requester = userRepository.findByEmail(requesterEmail.trim().toLowerCase())
              .orElseThrow(() -> new ResourceNotFoundException("Requester not found with email: " + requesterEmail));

      Optional<CommunityUser> communityUserOpt = communityUserRepository.findByCommunityIdAndUserId(
              community.getId(), requester.getId());

      if (communityUserOpt.isEmpty()) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, "You are not a member of this community", null));
      }

      CommunityUser communityUser = communityUserOpt.get();
      Role requesterRole = communityUser.getRole();

      boolean canViewRequests = requesterRole == Role.ADMIN || requesterRole == Role.WORKSPACE_OWNER;

      if (community.getCreatedBy() != null &&
              community.getCreatedBy().getId().equals(requester.getId())) {
        canViewRequests = true;
      }

      if (!canViewRequests) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(403, "Only admins or workspace owners can view pending requests", null));
      }

      List<PendingRequestUserDTO> pendingRequests = community.getPendingRequests().stream()
              .map(user -> new PendingRequestUserDTO(
                      user.getId(),
                      user.getUsername(),
                      user.getEmail()
              )).collect(Collectors.toList());

      return ResponseEntity.ok(new ApiResponse<>(200, "Pending requests fetched successfully", pendingRequests));

    }
    catch (ResourceNotFoundException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, ex.getMessage(), null));
    }
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "An unexpected error occurred: " + e.getMessage(), null));
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

      return ResponseEntity.ok(new ApiResponse<>(200, "All pending requests fetched",
        allRequests));

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

  private boolean isUserMemberOfCommunity(User user, Community community) {
    return community.getCommunityUsers() == null ||
      community.getCommunityUsers().stream()
        .noneMatch(cu -> cu.getUser().getId().equals(user.getId()) &&
          !cu.isBanned() && !cu.isBlocked());
  }

}
