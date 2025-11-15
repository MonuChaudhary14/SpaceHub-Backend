package org.spacehub.service.community;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.Community.CommunityInviteAcceptDTO;
import org.spacehub.DTO.Community.CommunityInviteRequestDTO;
import org.spacehub.DTO.Community.CommunityInviteResponseDTO;
import org.spacehub.DTO.Community.CommunityJoinResponseDTO;
import org.spacehub.DTO.Notification.NotificationRequestDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.Community.CommunityInvite;
import org.spacehub.entities.Community.InviteStatus;
import org.spacehub.entities.Community.CommunityUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.entities.Notification.NotificationType;
import org.spacehub.entities.User.User;
import org.spacehub.repository.User.UserRepository;
import org.spacehub.repository.community.CommunityInviteRepository;
import org.spacehub.repository.community.CommunityRepository;
import org.spacehub.repository.community.CommunityUserRepository;
import org.spacehub.service.Notification.NotificationService;
import org.spacehub.service.community.CommunityInterfaces.ICommunityInviteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Transactional
@RequiredArgsConstructor
@Service
public class CommunityInviteService implements ICommunityInviteService {

  private final CommunityInviteRepository inviteRepository;
  private final CommunityRepository communityRepository;
  private final CommunityUserRepository communityUserRepository;
  private final UserRepository userRepository;
  private final NotificationService notificationService;

  private String generateInviteCode() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  public ApiResponse<CommunityInviteResponseDTO> createInvite(UUID communityId, CommunityInviteRequestDTO request) {
    if (request == null || request.getInviterEmail() == null || request.getInviterEmail().isBlank()) {
      return new ApiResponse<>(400, "inviterEmail is required", null);
    }

    Community community = communityRepository.findById(communityId).orElse(null);
    if (community == null) {
      return new ApiResponse<>(404, "Community not found", null);
    }

    User inviter = userRepository.findByEmail(request.getInviterEmail()).orElse(null);
    if (inviter == null) {
      return new ApiResponse<>(404, "Inviter not found", null);
    }

    CommunityUser membership = communityUserRepository
            .findByCommunityIdAndUserId(communityId, inviter.getId())
            .orElse(null);

    if (membership == null) {
      return new ApiResponse<>(403, "You are not a member of this community", null);
    }

    Role role = membership.getRole();
    boolean hasPermission = (role == Role.ADMIN || role == Role.WORKSPACE_OWNER);

    if (!hasPermission) {
      return new ApiResponse<>(403, "Only admins or owners can create invites", null);
    }

    if (request.getMaxUses() <= 0) {
      return new ApiResponse<>(400, "maxUses must be >= 1", null);
    }

    if (request.getExpiresInHours() <= 0) {
      return new ApiResponse<>(400, "expiresInHours must be >= 1", null);
    }

    CommunityInvite invite = CommunityInvite.builder()
            .communityId(communityId)
            .inviterEmail(request.getInviterEmail())
            .maxUses(request.getMaxUses())
            .inviteCode(generateInviteCode())
            .expiresAt(LocalDateTime.now().plusHours(request.getExpiresInHours()))
            .status(InviteStatus.ACTIVE)
            .build();

    inviteRepository.save(invite);

    String inviteLink = "https://codewithketan.me/invite/"
            + communityId + "/" + invite.getInviteCode();

    CommunityInviteResponseDTO response = CommunityInviteResponseDTO.builder()
            .inviteCode(invite.getInviteCode())
            .inviteLink(inviteLink)
            .communityId(communityId)
            .inviterEmail(invite.getInviterEmail())
            .maxUses(invite.getMaxUses())
            .uses(invite.getUses())
            .expiresAt(invite.getExpiresAt())
            .status(invite.getStatus().name())
            .build();

    return new ApiResponse<>(200, "Invite created successfully", response);
  }

  @Override
  public ApiResponse<?> acceptInvite(CommunityInviteAcceptDTO request) {

    String inviteCode = extractInviteCode(request.getInviteCode());
    UUID communityId = request.getCommunityId();

    User user = userRepository.findByEmail(request.getAcceptorEmail()).orElse(null);
    if (user == null) {
      return new ApiResponse<>(404, "User not found", null);
    }

    Community community = communityRepository.findById(communityId).orElse(null);
    if (community == null) {
      return new ApiResponse<>(404, "Community not found", null);
    }

    CommunityInvite invite = validateInvite(inviteCode, communityId);
    if (invite == null) {
      return new ApiResponse<>(400, "Invalid or expired invite", null);
    }

    UUID notifRef = invite.getNotificationReference();
    if (notifRef != null) {
      notificationService.deleteActionableByReference(notifRef);
    }

    boolean isMember = communityUserRepository.findByCommunityAndUser(community, user).isPresent();
    if (isMember) {
      return new ApiResponse<>(400, "User already a member", community);
    }

    CommunityUser communityUser = new CommunityUser();
    communityUser.setCommunity(community);
    communityUser.setUser(user);
    communityUser.setRole(Role.MEMBER);
    communityUser.setJoinDate(LocalDateTime.now());

    communityUserRepository.save(communityUser);

    incrementInviteUsage(invite);

    NotificationRequestDTO notification = NotificationRequestDTO.builder()
            .senderEmail(user.getEmail())
            .email(invite.getInviterEmail())
            .type(NotificationType.COMMUNITY_JOINED)
            .title("New Member Joined")
            .message(user.getFirstName() + " joined your community.")
            .scope("community")
            .actionable(false)
            .referenceId(communityId)
            .communityId(communityId)
            .build();
    notificationService.createNotification(notification);

    return new ApiResponse<>(200, "User joined community successfully",
      new CommunityJoinResponseDTO(
        community.getId(), community.getName(), community.getDescription()
      )
    );

  }

  private String extractInviteCode(String rawCode) {
    return rawCode.contains("/") ? rawCode.substring(rawCode.lastIndexOf("/") + 1) : rawCode;
  }

  private CommunityInvite validateInvite(String inviteCode, UUID communityId) {
    Optional<CommunityInvite> inviteOpt = inviteRepository.findByInviteCode(inviteCode);
    if (inviteOpt.isEmpty()) return null;

    CommunityInvite invite = inviteOpt.get();

    if (!invite.getCommunityId().equals(communityId)) return null;

    if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
      invite.setStatus(InviteStatus.EXPIRED);
      inviteRepository.save(invite);
      return null;
    }

    if (invite.getUses() >= invite.getMaxUses()) {
      invite.setStatus(InviteStatus.USED);
      inviteRepository.save(invite);
      return null;
    }

    return invite;
  }

  private void incrementInviteUsage(CommunityInvite invite) {
    invite.setUses(invite.getUses() + 1);
    if (invite.getUses() >= invite.getMaxUses()) {
      invite.setStatus(InviteStatus.USED);
    }
    inviteRepository.save(invite);
  }

  @Override
  public ApiResponse<List<CommunityInviteResponseDTO>> getCommunityInvites(UUID communityId) {
    List<CommunityInviteResponseDTO> invites = inviteRepository.findByCommunityId(communityId).stream()
      .map(invite -> CommunityInviteResponseDTO.builder()
                    .inviteCode(invite.getInviteCode())
                    .inviteLink("https://codewithketan.me/invite/" + communityId + "/" + invite.getInviteCode())
                    .communityId(invite.getCommunityId())
                    .inviterEmail(invite.getInviterEmail())
                    .maxUses(invite.getMaxUses())
                    .uses(invite.getUses())
                    .expiresAt(invite.getExpiresAt())
                    .status(invite.getStatus().name())
                    .build())
            .collect(Collectors.toList());

    return new ApiResponse<>(200, "Invites fetched successfully", invites);
  }

  @Override
  public ApiResponse<String> revokeInvite(String inviteCode) {
    Optional<CommunityInvite> optionalInvite = inviteRepository.findByInviteCode(inviteCode);

    if (optionalInvite.isEmpty()) {
      return new ApiResponse<>(400, "Invite not found", null);
    }

    CommunityInvite invite = optionalInvite.get();
    UUID ref = invite.getNotificationReference();
    if (ref != null) {
      notificationService.deleteActionableByReference(ref);
    }
    inviteRepository.delete(invite);

    NotificationRequestDTO notification = NotificationRequestDTO.builder()
            .senderEmail("system@spacehub.com")
            .email(invite.getInviterEmail())
            .type(NotificationType.COMMUNITY_INVITE_REVOKED)
            .title("Invite Revoked")
            .message("Your community invite (" + invite.getInviteCode() + ") has been revoked.")
            .scope("community")
            .actionable(false)
            .referenceId(invite.getCommunityId())
            .communityId(invite.getCommunityId())
            .build();
    notificationService.createNotification(notification);

    return new ApiResponse<>(200, "Invite revoked successfully", null);
  }

}
