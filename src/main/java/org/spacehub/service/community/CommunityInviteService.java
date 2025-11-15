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
    ValidationContext ctx = new ValidationContext();
    ApiResponse<CommunityInviteResponseDTO> validationError = validateCreateInviteRequest(communityId, request, ctx);
    if (validationError != null) {
      return validationError;
    }

    CommunityInvite invite = buildInvite(communityId, request.getInviterEmail(), request.getMaxUses(),
      request.getExpiresInHours());
    inviteRepository.save(invite);

    CommunityInviteResponseDTO response = toDto(invite, communityId);
    return new ApiResponse<>(200, "Invite created successfully", response);
  }

  private static class ValidationContext {
    Community community;
    User inviter;
    CommunityUser membership;
  }

  private ApiResponse<CommunityInviteResponseDTO> validateCreateInviteRequest(UUID communityId,
                                                                              CommunityInviteRequestDTO request,
                                                                              ValidationContext ctx) {

    ApiResponse<CommunityInviteResponseDTO> err;

    err = validateRequestShape(request);
    if (err != null) {
      return err;
    }

    err = loadCommunity(communityId, ctx);
    if (err != null) {
      return err;
    }

    err = loadInviter(request.getInviterEmail(), ctx);
    if (err != null) {
      return err;
    }

    err = loadMembership(communityId, ctx);
    if (err != null) {
      return err;
    }

    err = authorizeInviter(ctx);
    if (err != null) {
      return err;
    }

    err = validateLimits(request);
    return err;

  }

  private ApiResponse<CommunityInviteResponseDTO> validateRequestShape(CommunityInviteRequestDTO request) {
    if (request == null || request.getInviterEmail() == null || request.getInviterEmail().isBlank()) {
      return new ApiResponse<>(400, "inviterEmail is required", null);
    }
    return null;
  }

  private ApiResponse<CommunityInviteResponseDTO> loadCommunity(UUID communityId, ValidationContext ctx) {
    Community community = communityRepository.findById(communityId).orElse(null);
    if (community == null) {
      return new ApiResponse<>(404, "Community not found", null);
    }
    ctx.community = community;
    return null;
  }

  private ApiResponse<CommunityInviteResponseDTO> loadInviter(String inviterEmail, ValidationContext ctx) {
    User inviter = userRepository.findByEmail(inviterEmail).orElse(null);
    if (inviter == null) {
      return new ApiResponse<>(404, "Inviter not found", null);
    }
    ctx.inviter = inviter;
    return null;
  }

  private ApiResponse<CommunityInviteResponseDTO> loadMembership(UUID communityId, ValidationContext ctx) {
    CommunityUser membership = communityUserRepository
      .findByCommunityIdAndUserId(communityId, ctx.inviter.getId())
      .orElse(null);
    if (membership == null) {
      return new ApiResponse<>(403, "You are not a member of this community", null);
    }
    ctx.membership = membership;
    return null;
  }

  private ApiResponse<CommunityInviteResponseDTO> authorizeInviter(ValidationContext ctx) {
    Role role = ctx.membership.getRole();
    if (!(role == Role.ADMIN || role == Role.WORKSPACE_OWNER)) {
      return new ApiResponse<>(403, "Only admins or owners can create invites", null);
    }
    return null;
  }

  private ApiResponse<CommunityInviteResponseDTO> validateLimits(CommunityInviteRequestDTO request) {
    if (request.getMaxUses() <= 0) {
      return new ApiResponse<>(400, "maxUses must be >= 1", null);
    }
    if (request.getExpiresInHours() <= 0) {
      return new ApiResponse<>(400, "expiresInHours must be >= 1", null);
    }
    return null;
  }

  private CommunityInvite buildInvite(UUID communityId, String inviterEmail, int maxUses, int expiresInHours) {
    return CommunityInvite.builder()
      .communityId(communityId)
      .inviterEmail(inviterEmail)
      .maxUses(maxUses)
      .inviteCode(generateInviteCode())
      .expiresAt(LocalDateTime.now().plusHours(expiresInHours))
      .status(InviteStatus.ACTIVE)
      .build();
  }

  private CommunityInviteResponseDTO toDto(CommunityInvite invite, UUID communityId) {
    String inviteLink = "https://codewithketan.me/invite/" + communityId + "/" + invite.getInviteCode();
    return CommunityInviteResponseDTO.builder()
      .inviteCode(invite.getInviteCode())
      .inviteLink(inviteLink)
      .communityId(communityId)
      .inviterEmail(invite.getInviterEmail())
      .maxUses(invite.getMaxUses())
      .uses(invite.getUses())
      .expiresAt(invite.getExpiresAt())
      .status(invite.getStatus().name())
      .build();
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
    int i = rawCode.lastIndexOf('/');
    if (i == -1) {
      return rawCode;
    }
    return rawCode.substring(i + 1);
  }

  private CommunityInvite validateInvite(String inviteCode, UUID communityId) {
    Optional<CommunityInvite> inviteOpt = inviteRepository.findByInviteCode(inviteCode);
    if (inviteOpt.isEmpty()) {
      return null;
    }

    CommunityInvite invite = inviteOpt.get();

    if (!invite.getCommunityId().equals(communityId)) {
      return null;
    }

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
