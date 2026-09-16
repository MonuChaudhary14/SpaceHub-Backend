package org.spacehub.service.community;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.Community.CommunityBlockRequest;
import org.spacehub.DTO.Community.CommunityChangeRoleRequest;
import org.spacehub.DTO.Community.CommunityMemberDTO;
import org.spacehub.DTO.Community.CommunityMemberRequest;
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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class CommunityMemberRoleService {

  private final CommunityRepository communityRepository;
  private final UserRepository userRepository;
  private final CommunityUserRepository communityUserRepository;
  private final NotificationService notificationService;
  private final CommunityMediaService communityMediaService;

  public ResponseEntity<ApiResponse<String>> removeMemberFromCommunity(CommunityMemberRequest request) {
    try {
      Community community = communityRepository.findByIdWithUsers(request.getCommunityId())
        .orElseThrow(() -> new RuntimeException("Community not found"));
      User requester = userRepository.findByEmail(SecurityUtils.getCurrentUserEmail())
        .orElseThrow(() -> new RuntimeException("Requester not found"));
      User target = userRepository.findByEmail(request.getUserEmail())
        .orElseThrow(() -> new RuntimeException("Target user not found"));

      if (community.getCreatedBy() != null && community.getCreatedBy().getId().equals(target.getId())) {
        return ResponseEntity.status(403)
          .body(new ApiResponse<>(403, "Cannot remove community creator", null));
      }

      CommunityUser requesterCU = getCommunityUser(community, requester, "Requester is not a member of this community");
      CommunityUser targetCU = getCommunityUser(community, target, "User is not a member of this community");

      ResponseEntity<ApiResponse<String>> permissionCheck = checkRemovalPermissions(community, requesterCU, targetCU);
      if (permissionCheck != null) {
        return permissionCheck;
      }

      performRemoval(community, target, targetCU);
      notifyRemovedUser(community, requester, target);

      return ResponseEntity.ok(new ApiResponse<>(200, "Member removed successfully", null));
    } catch (RuntimeException ex) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, ex.getMessage(), null));
    } catch (Exception e) {
      return ResponseEntity.internalServerError()
        .body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  public ResponseEntity<ApiResponse<String>> changeMemberRole(CommunityChangeRoleRequest request) {
    try {
      if (request.getCommunityId() == null || request.getTargetUserEmail() == null || request.getNewRole() == null) {
        throw new IllegalArgumentException("Check the fields");
      }

      Community community = communityRepository.findById(request.getCommunityId())
        .orElseThrow(() -> new IllegalArgumentException("Community not found"));
      User requester = userRepository.findByEmail(SecurityUtils.getCurrentUserEmail())
        .orElseThrow(() -> new IllegalArgumentException("Requester not found"));
      User target = userRepository.findByEmail(request.getTargetUserEmail())
        .orElseThrow(() -> new IllegalArgumentException("Target user not found"));

      boolean isReqMember = communityUserRepository.findByCommunityIdAndUserId(community.getId(), requester.getId()).isPresent();
      boolean isTarMember = communityUserRepository.findByCommunityIdAndUserId(community.getId(), target.getId()).isPresent();

      if (!isReqMember || !isTarMember) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403, "User is not a member of this community", null));
      }

      Role requesterRole = getUserRoleInCommunity(community, requester);
      Role targetRole = getUserRoleInCommunity(community, target);
      Role newRole = Role.valueOf(request.getNewRole().toUpperCase());

      if (!community.getCreatedBy().getId().equals(requester.getId())) {
        throw new SecurityException("Only the community creator can change roles");
      }

      if (!canChangeRole(requesterRole, targetRole, newRole)) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403, "You do not have permission to change this user's role", null));
      }

      if (targetRole == newRole) {
        return ResponseEntity.ok(new ApiResponse<>(200, "User already has this role", null));
      }

      CommunityUser communityUser = community.getCommunityUsers().stream()
        .filter(cu -> cu.getUser().getId().equals(target.getId()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("User is not a member"));

      communityUser.setRole(newRole);
      communityUserRepository.save(communityUser);

      return ResponseEntity.ok(new ApiResponse<>(200, "Role of " + target.getEmail() + " changed to " + newRole, null));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    } catch (SecurityException e) {
      return ResponseEntity.status(403).body(new ApiResponse<>(403, e.getMessage(), null));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
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
          .avatarPreviewUrl(communityMediaService.generatePresignedSafely(user.getAvatarUrl()))
          .bio(user.getBio())
          .build();
      })
      .collect(Collectors.toList());

    Map<String, Object> response = new HashMap<>();
    response.put("communityId", community.getId());
    response.put("communityName", community.getName());
    response.put("totalMembers", members.size());
    response.put("members", members);

    return ResponseEntity.ok(new ApiResponse<>(200, "Community members fetched successfully", response));
  }

  public ResponseEntity<ApiResponse<String>> blockOrUnblockMember(CommunityBlockRequest request) {
    try {
      Community community = communityRepository.findById(request.getCommunityId())
        .orElseThrow(() -> new RuntimeException("Community not found"));
      User requester = userRepository.findByEmail(SecurityUtils.getCurrentUserEmail())
        .orElseThrow(() -> new RuntimeException("Requester not found"));
      User target = userRepository.findByEmail(request.getTargetUserEmail())
        .orElseThrow(() -> new RuntimeException("Target user not found"));

      CommunityUser reqCU = communityUserRepository.findByCommunityIdAndUserId(community.getId(), requester.getId())
        .orElseThrow(() -> new RuntimeException("Requester is not a member of this community"));
      CommunityUser tarCU = communityUserRepository.findByCommunityIdAndUserId(community.getId(), target.getId())
        .orElseThrow(() -> new RuntimeException("Target is not a member of this community"));

      Role requesterRole = reqCU.getRole();
      Role targetRole = tarCU.getRole();

      if (!canRequesterBlockTarget(requesterRole, targetRole)) {
        return ResponseEntity.status(403)
          .body(new ApiResponse<>(403, "You do not have permission to block or unblock this user", null));
      }

      tarCU.setBanned(request.isBlock());
      communityUserRepository.save(tarCU);

      String blocked = request.isBlock() ? "blocked" : "unblocked";
      return ResponseEntity.ok(new ApiResponse<>(200, "User " + target.getEmail() + " has been " + blocked + " successfully", null));
    } catch (RuntimeException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  public ResponseEntity<?> getRolesForRequester(UUID communityId) {
    String requesterEmail = SecurityUtils.getCurrentUserEmail();
    if (communityId == null || requesterEmail == null || requesterEmail.isBlank()) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "communityId and requesterEmail are required", null));
    }

    try {
      Community community = communityRepository.findById(communityId)
        .orElseThrow(() -> new RuntimeException("Community not found"));
      User requester = userRepository.findByEmail(requesterEmail.trim().toLowerCase())
        .orElseThrow(() -> new RuntimeException("User not found"));

      Optional<CommunityUser> cuOpt = communityUserRepository.findByCommunityIdAndUserId(community.getId(), requester.getId());
      if (cuOpt.isEmpty()) {
        return ResponseEntity.status(403).body(new ApiResponse<>(403, "You are not a member of this community", null));
      }

      Role role = cuOpt.get().getRole();
      Map<String, Object> roleInfo = new HashMap<>();
      roleInfo.put("role", role.toString());
      roleInfo.put("isAdmin", role == Role.ADMIN);
      roleInfo.put("isWorkspaceOwner", role == Role.WORKSPACE_OWNER);
      roleInfo.put("isMember", role == Role.MEMBER);
      roleInfo.put("isCreator", community.getCreatedBy() != null && community.getCreatedBy().getId().equals(requester.getId()));

      return ResponseEntity.ok(new ApiResponse<>(200, "Role details fetched successfully", roleInfo));
    } catch (RuntimeException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null));
    }
  }

  private boolean canRequesterBlockTarget(Role requesterRole, Role targetRole) {
    return switch (requesterRole) {
      case ADMIN -> true;
      case WORKSPACE_OWNER -> targetRole == Role.MEMBER;
      default -> false;
    };
  }

  private boolean canChangeRole(Role requesterRole, Role targetRole, Role newRole) {
    return switch (requesterRole) {
      case ADMIN -> true;
      case WORKSPACE_OWNER -> (targetRole == Role.MEMBER && newRole == Role.WORKSPACE_OWNER);
      default -> false;
    };
  }

  private Role getUserRoleInCommunity(Community community, User user) {
    return community.getCommunityUsers().stream()
      .filter(cu -> cu.getUser().getId().equals(user.getId()))
      .map(CommunityUser::getRole)
      .findFirst()
      .orElse(Role.MEMBER);
  }

  private CommunityUser getCommunityUser(Community c, User u, String errorMessage) {
    return c.getCommunityUsers().stream()
      .filter(cu -> cu.getUser().getId().equals(u.getId()))
      .findFirst()
      .orElseThrow(() -> new RuntimeException(errorMessage));
  }

  private ResponseEntity<ApiResponse<String>> checkRemovalPermissions(
    Community community, CommunityUser requesterCU, CommunityUser targetCU) {

    boolean isCreator = community.getCreatedBy().getId().equals(requesterCU.getUser().getId());
    Role reqRole = requesterCU.getRole();
    Role tarRole = targetCU.getRole();

    if (isCreator) {
      return null;
    }

    if (reqRole == Role.WORKSPACE_OWNER && tarRole == Role.WORKSPACE_OWNER) {
      return ResponseEntity.status(403).body(new ApiResponse<>(403, "Cannot remove another workspace owner", null));
    }

    if (reqRole == Role.ADMIN && (tarRole == Role.ADMIN || tarRole == Role.WORKSPACE_OWNER)) {
      return ResponseEntity.status(403).body(new ApiResponse<>(403, "Admins can only remove members, not other admins or workspace owners", null));
    }

    if (reqRole == Role.MEMBER) {
      return ResponseEntity.status(403).body(new ApiResponse<>(403, "You do not have permission to remove this member", null));
    }

    return null;
  }

  private void performRemoval(Community community, User target, CommunityUser targetCU) {
    if (community.getMembers() != null) {
      community.getMembers().removeIf(u -> u.getId().equals(target.getId()));
    }
    if (community.getCommunityUsers() != null) {
      community.getCommunityUsers().removeIf(cu -> cu.getId().equals(targetCU.getId()));
    }
    communityUserRepository.delete(targetCU);
    communityRepository.save(community);
  }

  private void notifyRemovedUser(Community community, User requester, User target) {
    NotificationRequestDTO req = NotificationRequestDTO.builder()
      .senderEmail(requester.getEmail())
      .email(target.getEmail())
      .title("You were removed from a community")
      .message("An admin has removed you from the community '" + community.getName() + "'.")
      .type(NotificationType.COMMUNITY_MEMBER_REMOVED)
      .scope("community")
      .communityId(community.getId())
      .build();
    notificationService.createNotification(req);
  }
}
