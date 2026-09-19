package org.spacehub.service.community;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.Community.AcceptRequest;
import org.spacehub.DTO.Community.CancelJoinRequest;
import org.spacehub.DTO.Community.CommunityPendingRequestDTO;
import org.spacehub.DTO.Community.JoinCommunity;
import org.spacehub.DTO.Community.LeaveCommunity;
import org.spacehub.DTO.Community.PendingRequestUserDTO;
import org.spacehub.DTO.Community.RejectRequest;
import org.spacehub.DTO.Notification.NotificationRequestDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.Community.CommunityUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.entities.Notification.NotificationType;
import org.spacehub.entities.User.User;
import org.spacehub.repository.User.UserRepository;
import org.spacehub.repository.community.CommunityRepository;
import org.spacehub.repository.community.CommunityUserRepository;
import org.spacehub.service.Notification.NotificationService;
import org.spacehub.utils.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class CommunityMembershipService {

  private final CommunityRepository communityRepository;
  private final UserRepository userRepository;
  private final CommunityUserRepository communityUserRepository;
  private final NotificationService notificationService;
  private final CommunityRoomService communityRoomService;

  public ResponseEntity<ApiResponse<?>> requestToJoinCommunity(JoinCommunity joinCommunity) {
    try {
      if (joinCommunity == null || joinCommunity.getCommunityId() == null
          && (joinCommunity.getCommunityName() == null || joinCommunity.getCommunityName().trim().isEmpty())) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community name or ID is required", null));
      }

      Community community = findCommunityByNameOrId(joinCommunity.getCommunityName(), joinCommunity.getCommunityId());
      User user = userRepository.findByEmail(SecurityUtils.getCurrentUserEmail())
        .orElseThrow(() -> new RuntimeException("User not found"));

      Optional<CommunityUser> existingMember = community.getCommunityUsers().stream()
        .filter(cu -> cu.getUser().getId().equals(user.getId()))
        .findFirst();

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

      notifyCommunityAdmins(community, user);

      return ResponseEntity.ok().body(new ApiResponse<>(200, "Request sent to community"));
    }
    catch (RuntimeException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    }
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  public ResponseEntity<ApiResponse<?>> cancelRequestCommunity(CancelJoinRequest cancelJoinRequest) {
    try {
      Community community = findCommunityByNameOrId(cancelJoinRequest.getCommunityName(), cancelJoinRequest.getCommunityId());
      User user = userRepository.findByEmail(SecurityUtils.getCurrentUserEmail())
        .orElseThrow(() -> new RuntimeException("User not found"));

      if (!community.getPendingRequests().contains(user)) {
        return ResponseEntity.status(403).body(
          new ApiResponse<>(403, "No join request found for this user in the community", null)
        );
      }

      if (community.getCommunityUsers().stream().anyMatch(cu -> cu.getUser().getId().equals(user.getId())
          && (cu.isBanned() || cu.isBlocked()))) {
        return ResponseEntity.status(403).body(
          new ApiResponse<>(403, "You are not allowed to cancel requests in this community", null)
        );
      }

      community.getPendingRequests().remove(user);
      communityRepository.save(community);

      community.getCommunityUsers().stream()
        .filter(cu -> cu.getRole() == Role.OWNER || cu.getRole() == Role.ADMIN || cu.getRole() == Role.MODERATOR)
        .forEach(adminCU -> {
          User admin = adminCU.getUser();
          notificationService.createNotification(
            NotificationRequestDTO.builder()
              .email(admin.getEmail())
              .senderEmail(user.getEmail())
              .type(NotificationType.COMMUNITY_INVITE_REVOKED)
              .title("Join Request Cancelled")
              .message("User " + user.getUsername() + " cancelled their request to join " + community.getName())
              .scope("community")
              .communityId(community.getId())
              .referenceId(community.getId())
              .build()
          );
        });

      return ResponseEntity.ok(new ApiResponse<>(200, "Cancelled the join request successfully", null));
    }
    catch (RuntimeException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    }
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  public ResponseEntity<ApiResponse<?>> acceptRequest(AcceptRequest acceptRequest) {
    if (acceptRequest == null || isBlank(acceptRequest.getUserEmail())
        || isBlank(acceptRequest.getCommunityName()) && acceptRequest.getCommunityId() == null) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields", null));
    }

    try {
      String creatorEmail = SecurityUtils.getCurrentUserEmail();
      String userEmail = acceptRequest.getUserEmail().trim().toLowerCase();
      String communityName = acceptRequest.getCommunityName() != null ? acceptRequest.getCommunityName().trim() : null;

      Community community = findCommunityByNameOrId(communityName, acceptRequest.getCommunityId());
      User creator = userRepository.findByEmail(creatorEmail).orElseThrow(() -> new RuntimeException("Creator not found"));
      User user = userRepository.findByEmail(userEmail).orElseThrow(() -> new RuntimeException("User not found"));

      if (!hasPermissionToAccept(community, creator)) {
        return ResponseEntity.status(403).body(
          new ApiResponse<>(403, "Only community owners, admins, or moderators can accept requests", null)
        );
      }

      if (!hasPendingRequest(community, user)) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "No pending request from this user", null));
      }

      if (!isUserMemberOfCommunity(creator, community)) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403, "You are no longer an active member of this community", null));
      }

      community.getPendingRequests().removeIf(u -> u != null && u.getId() != null && u.getId().equals(user.getId()));
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

      notificationService.createNotification(
        NotificationRequestDTO.builder()
          .email(user.getEmail())
          .senderEmail(creator.getEmail())
          .type(NotificationType.COMMUNITY_REQUEST_ACCEPTED)
          .title("Join Request Accepted")
          .message("You have been accepted into the community " + community.getName())
          .scope("community")
          .communityId(community.getId())
          .referenceId(community.getId())
          .build()
      );

      return ResponseEntity.ok(new ApiResponse<>(200, "User has been added to the community successfully", null));
    }
    catch (RuntimeException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    }
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  public ResponseEntity<?> leaveCommunity(LeaveCommunity leaveCommunity) {
    if (leaveCommunity == null || isBlank(leaveCommunity.getCommunityName()) && leaveCommunity.getCommunityId() == null) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields", null));
    }

    try {
      Community community = findCommunityByNameOrId(leaveCommunity.getCommunityName(), leaveCommunity.getCommunityId());
      User user = userRepository.findByEmail(SecurityUtils.getCurrentUserEmail())
        .orElseThrow(() -> new RuntimeException("User not found"));

      if (community.getCreatedBy() != null && community.getCreatedBy().getId().equals(user.getId())) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403,
          "Community creator cannot leave the community. Transfer ownership or delete the community instead.", null));
      }

      CommunityUser communityUser = community.getCommunityUsers().stream()
        .filter(cu -> cu.getUser().getId().equals(user.getId()))
        .findFirst()
        .orElse(null);

      if (communityUser == null) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "You are not a member of this community", null));
      }

      communityUserRepository.delete(communityUser);
      if (community.getCommunityUsers() != null) {
        community.getCommunityUsers().removeIf(cu ->
          cu.getId() != null && cu.getId().equals(communityUser.getId()) ||
            cu.getUser() != null && cu.getUser().getId().equals(user.getId())
        );
      }
      communityRepository.save(community);

      notifyAdmins(community, user, "Member Left",
        "User '" + user.getUsername() + "' has left your community '" + community.getName() + "'.");

      return ResponseEntity.ok(new ApiResponse<>(200, "You have left the community successfully", null));
    }
    catch (RuntimeException ex) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, ex.getMessage(), null));
    }
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  public ResponseEntity<?> rejectRequest(RejectRequest rejectRequest) {
    if (rejectRequest == null || isBlank(rejectRequest.getCommunityName()) && rejectRequest.getCommunityId() == null
        || isBlank(rejectRequest.getUserEmail())) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields", null));
    }

    try {
      String creatorEmail = SecurityUtils.getCurrentUserEmail();
      String userEmail = rejectRequest.getUserEmail().trim().toLowerCase();
      String communityName = rejectRequest.getCommunityName() != null ? rejectRequest.getCommunityName().trim() : null;

      Community community = findCommunityByNameOrId(communityName, rejectRequest.getCommunityId());
      User creator = userRepository.findByEmail(creatorEmail).orElseThrow(() -> new RuntimeException("Creator not found"));
      User user = userRepository.findByEmail(userEmail).orElseThrow(() -> new RuntimeException("User not found"));

      if (!isUserMemberOfCommunity(creator, community)) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403, "You are no longer a member of this community", null));
      }

      boolean isCreator = community.getCreatedBy() != null && community.getCreatedBy().getId().equals(creator.getId());
      boolean isStaff = community.getCommunityUsers() != null &&
        community.getCommunityUsers().stream()
          .anyMatch(cu -> cu.getUser().getId().equals(creator.getId()) &&
            (cu.getRole() == Role.OWNER || cu.getRole() == Role.ADMIN || cu.getRole() == Role.MODERATOR));

      if (!isStaff && !isCreator) {
        return ResponseEntity.status(403).body(
          new ApiResponse<>(403, "Only community owners, admins, or moderators can reject requests", null)
        );
      }

      if (!hasPendingRequest(community, user)) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "No pending request from this user", null));
      }

      community.getPendingRequests().removeIf(u -> u != null && u.getId() != null && u.getId().equals(user.getId()));
      communityRepository.save(community);

      notificationService.createNotification(
        NotificationRequestDTO.builder()
          .email(user.getEmail())
          .senderEmail(creator.getEmail())
          .type(NotificationType.COMMUNITY_INVITE_REVOKED)
          .title("Join Request Rejected")
          .message("Your request to join " + community.getName() + " has been rejected.")
          .scope("community-request")
          .actionable(false)
          .communityId(community.getId())
          .referenceId(community.getId())
          .build()
      );

      return ResponseEntity.ok(new ApiResponse<>(200, "Join request rejected successfully", null));
    }
    catch (RuntimeException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    }
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  public ResponseEntity<?> enterOrRequestCommunity(UUID communityId) {
    String requesterEmail = SecurityUtils.getCurrentUserEmail();
    if (communityId == null || requesterEmail == null || requesterEmail.isBlank()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "communityId and requesterEmail are required", null));
    }

    try {
      Community community = communityRepository.findById(communityId)
        .orElseThrow(() -> new RuntimeException("Community not found"));
      User user = userRepository.findByEmail(requesterEmail.trim().toLowerCase())
        .orElseThrow(() -> new RuntimeException("User not found"));

      boolean isBanned = community.getCommunityUsers().stream()
        .anyMatch(cu -> cu.getUser().getId().equals(user.getId()) && cu.isBanned());
      if (isBanned) {
        return ResponseEntity.status(403).body(
          new ApiResponse<>(403, "You are banned from this community", Map.of("accessDenied", true))
        );
      }

      boolean isMember = community.getCommunityUsers().stream()
        .anyMatch(cu -> cu.getUser().getId().equals(user.getId()));
      if (isMember) {
        return communityRoomService.getCommunityWithRooms(communityId);
      }

      boolean isPending = community.getPendingRequests().stream()
        .anyMatch(u -> u.getId().equals(user.getId()));
      if (isPending) {
        return ResponseEntity.ok(new ApiResponse<>(200, "Join request already pending",
          Map.of("requested", true, "message", "Join request already pending")));
      }

      community.getPendingRequests().add(user);
      communityRepository.save(community);

      notifyCommunityAdmins(community, user);

      return ResponseEntity.ok(new ApiResponse<>(200, "Join request sent successfully",
        Map.of("requested", true, "message", "Join request sent successfully")));
    }
    catch (RuntimeException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    }
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  public ResponseEntity<ApiResponse<?>> getPendingRequests(UUID communityId) {
    try {
      String requesterEmail = SecurityUtils.getCurrentUserEmail();
      if (communityId == null || requesterEmail == null || requesterEmail.isBlank()) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, "communityId and requesterEmail are required", null));
      }

      Community community = communityRepository.findById(communityId)
        .orElseThrow(() -> new RuntimeException("Community not found with ID: " + communityId));
      User requester = userRepository.findByEmail(requesterEmail.trim().toLowerCase())
        .orElseThrow(() -> new RuntimeException("Requester not found with email: " + requesterEmail));

      Optional<CommunityUser> communityUserOpt =
        communityUserRepository.findByCommunityIdAndUserId(community.getId(), requester.getId());
      if (communityUserOpt.isEmpty()) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, "You are not a member of this community", null));
      }

      Role requesterRole = communityUserOpt.get().getRole();
      boolean canViewRequests = requesterRole == Role.OWNER || requesterRole == Role.ADMIN || requesterRole == Role.MODERATOR
        || community.getCreatedBy() != null && community.getCreatedBy().getId().equals(requester.getId());

      if (!canViewRequests) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
          new ApiResponse<>(403, "Only owners, admins, or moderators can view pending requests", null)
        );
      }

      List<PendingRequestUserDTO> pendingRequests = community.getPendingRequests().stream()
        .map(user -> new PendingRequestUserDTO(user.getId(), user.getUsername(), user.getEmail()))
        .collect(Collectors.toList());

      return ResponseEntity.ok(new ApiResponse<>(200, "Pending requests fetched successfully", pendingRequests));
    }
    catch (RuntimeException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, ex.getMessage(), null));
    }
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "An unexpected error occurred: " + e.getMessage(), null));
    }
  }

  public ResponseEntity<ApiResponse<?>> getAllPendingRequestsForAdmin() {
    try {
      String requesterEmail = SecurityUtils.getCurrentUserEmail();
      User adminUser = userRepository.findByEmail(requesterEmail)
        .orElseThrow(() -> new RuntimeException("User not found with email: " + requesterEmail));

      List<CommunityUser> staffRoles = new ArrayList<>();
      staffRoles.addAll(communityUserRepository.findByUserAndRole(adminUser, Role.OWNER));
      staffRoles.addAll(communityUserRepository.findByUserAndRole(adminUser, Role.ADMIN));
      staffRoles.addAll(communityUserRepository.findByUserAndRole(adminUser, Role.MODERATOR));
      List<CommunityPendingRequestDTO> allRequests = new ArrayList<>();

      for (CommunityUser staffRole : staffRoles) {
        Community community = staffRole.getCommunity();
        List<PendingRequestUserDTO> pendingRequests = community.getPendingRequests().stream()
          .map(user -> new PendingRequestUserDTO(user.getId(), user.getUsername(), user.getEmail()))
          .collect(Collectors.toList());

        if (!pendingRequests.isEmpty()) {
          allRequests.add(new CommunityPendingRequestDTO(community.getId(), community.getName(), pendingRequests));
        }
      }

      return ResponseEntity.ok(new ApiResponse<>(200, "All pending requests fetched", allRequests));
    }
    catch (RuntimeException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, ex.getMessage(), null));
    }
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "An unexpected error occurred: " + e.getMessage(), null));
    }
  }

  private boolean hasPermissionToAccept(Community community, User creator) {
    boolean isCreator = community.getCreatedBy() != null && community.getCreatedBy().getId().equals(creator.getId());
    boolean isStaff = community.getCommunityUsers() != null &&
      community.getCommunityUsers().stream()
        .anyMatch(cu -> cu.getUser().getId().equals(creator.getId()) &&
          (cu.getRole() == Role.OWNER || cu.getRole() == Role.ADMIN || cu.getRole() == Role.MODERATOR));
    return isCreator || isStaff;
  }

  private boolean hasPendingRequest(Community community, User user) {
    return community.getPendingRequests() != null &&
      community.getPendingRequests().stream()
        .anyMatch(u -> u != null && u.getId() != null && u.getId().equals(user.getId()));
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

  private void notifyCommunityAdmins(Community community, User user) {
    community.getCommunityUsers().stream()
      .filter(cu -> cu.getRole() == Role.OWNER || cu.getRole() == Role.ADMIN || cu.getRole() == Role.MODERATOR)
      .forEach(adminCU -> {
        User admin = adminCU.getUser();
        notificationService.createNotification(
          NotificationRequestDTO.builder()
            .email(admin.getEmail())
            .senderEmail(user.getEmail())
            .type(NotificationType.COMMUNITY_JOINED)
            .title("Community Join Request")
            .message(user.getUsername() + " requested to join " + community.getName())
            .scope("community-request")
            .actionable(true)
            .communityId(community.getId())
            .referenceId(community.getId())
            .build()
        );
      });
  }

  private void notifyAdmins(Community community, User sender, String title, String message) {
    community.getCommunityUsers().stream()
      .filter(cu -> cu.getRole() == Role.OWNER || cu.getRole() == Role.ADMIN || cu.getRole() == Role.MODERATOR)
      .map(CommunityUser::getUser)
      .forEach(admin -> {
        if (!admin.equals(sender)) {
          notificationService.createNotification(
            NotificationRequestDTO.builder()
              .senderEmail(sender.getEmail())
              .email(admin.getEmail())
              .title(title)
              .message(message)
              .type(NotificationType.COMMUNITY_MEMBER_LEFT)
              .scope("community")
              .communityId(community.getId())
              .build()
          );
        }
      });
  }

  private Community findCommunityByNameOrId(String name, UUID communityId) {
    if (communityId != null) {
      Optional<Community> c = communityRepository.findById(communityId);
      if (c.isPresent()) {
        return c.get();
      }
    }
    if (name != null && !name.isBlank()) {
      Community c = communityRepository.findByName(name.trim());
      if (c != null) {
        return c;
      }
      try {
        UUID id = UUID.fromString(name.trim());
        Optional<Community> cById = communityRepository.findById(id);
        if (cById.isPresent()) {
          return cById.get();
        }
      }
      catch (IllegalArgumentException ignored) {
      }
    }
    throw new RuntimeException("Community not found");
  }

  private boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
