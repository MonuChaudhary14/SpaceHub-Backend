package org.spacehub.service.LocalRoom;

import org.spacehub.DTO.LocalGroup.LocalGroupInviteAcceptDTO;
import org.spacehub.DTO.LocalGroup.LocalGroupInviteRequestDTO;
import org.spacehub.DTO.LocalGroup.LocalGroupInviteResponseDTO;
import org.spacehub.DTO.LocalGroup.LocalGroupJoinResponseDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.LocalGroup.LocalGroup;
import org.spacehub.entities.LocalGroup.LocalGroupInvite;
import org.spacehub.entities.User.User;
import org.spacehub.repository.User.UserRepository;
import org.spacehub.repository.localgroup.LocalGroupInviteRepository;
import org.spacehub.repository.localgroup.LocalGroupRepository;
import org.spacehub.service.Interface.ILocalGroupInviteService;
import org.spacehub.service.Notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Transactional
@Service
public class LocalGroupInviteService implements ILocalGroupInviteService {

  private final LocalGroupInviteRepository inviteRepository;
  private final LocalGroupRepository groupRepository;
  private final UserRepository userRepository;
  private final NotificationService notificationService;

  public LocalGroupInviteService(LocalGroupInviteRepository inviteRepository,
                                 LocalGroupRepository groupRepository,
                                 UserRepository userRepository,
                                 NotificationService notificationService) {
    this.inviteRepository = inviteRepository;
    this.groupRepository = groupRepository;
    this.userRepository = userRepository;
    this.notificationService = notificationService;
  }

  private String generateInviteCode() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  @Override
  public ApiResponse<LocalGroupInviteResponseDTO> createInvite(UUID groupId, LocalGroupInviteRequestDTO request) {

    Optional<LocalGroup> optionalGroup = groupRepository.findById(groupId);
    if (optionalGroup.isEmpty()) {
      return new ApiResponse<>(404, "Local group not found", null);
    }

    LocalGroupInvite invite = LocalGroupInvite.builder()
            .localGroup(optionalGroup.get())
            .inviterEmail(request.getInviterEmail())
            .maxUses(request.getMaxUses())
            .inviteCode(generateInviteCode())
            .expiresAt(LocalDateTime.now().plusHours(request.getExpiresInHours()))
            .createdAt(LocalDateTime.now())
            .build();

    inviteRepository.save(invite);

    String inviteLink = String.format("https://codewithketan.me.localgroup/invite/%s/%s", groupId, invite.getInviteCode());

    LocalGroupInviteResponseDTO response = LocalGroupInviteResponseDTO.builder()
            .inviteCode(invite.getInviteCode())
            .inviteLink(inviteLink)
            .groupId(groupId)
            .inviterEmail(invite.getInviterEmail())
            .maxUses(invite.getMaxUses())
            .uses(invite.getUses())
            .expiresAt(invite.getExpiresAt())
            .status("ACTIVE")
            .build();

    return new ApiResponse<>(200, "Invite created successfully", response);
  }

  @Override
  public ApiResponse<?> acceptInvite(LocalGroupInviteAcceptDTO request) {

    String acceptorEmail = request.getAcceptorEmail();
    if (acceptorEmail == null || acceptorEmail.isBlank()) {
      return badRequest("Acceptor email is required");
    }

    String inviteCode = extractInviteCode(request.getInviteCode());
    Optional<LocalGroupInvite> inviteOpt = inviteRepository.findByInviteCode(inviteCode);

    if (inviteOpt.isEmpty()) {
      return badRequest("Invalid invite link");
    }

    LocalGroupInvite invite = inviteOpt.get();

    ApiResponse<?> validationError = validateInvite(invite, request.getGroupId());
    if (validationError != null) {
      return validationError;
    }

    Optional<User> optionalUser = userRepository.findByEmail(acceptorEmail);
    if (optionalUser.isEmpty()) {
      return notFound();
    }

    User user = optionalUser.get();
    LocalGroup group = invite.getLocalGroup();

    if (isAlreadyMember(group, user)) {
      return badRequest("User is already a member of this local group");
    }

    addMemberAndUpdateInvite(group, invite, user);

    notifyInviter(invite, user, request.getGroupId());

    return success(group);
  }

  private String extractInviteCode(String rawCode) {
    return rawCode.contains("/") ? rawCode.substring(rawCode.lastIndexOf("/") + 1) : rawCode;
  }

  private ApiResponse<?> validateInvite(LocalGroupInvite invite, UUID groupId) {
    if (!invite.getLocalGroup().getId().equals(groupId)) {
      return badRequest("Invite does not belong to this local group");
    }

    if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
      inviteRepository.delete(invite);
      return badRequest("Invite link has expired");
    }

    if (invite.getUses() >= invite.getMaxUses()) {
      inviteRepository.delete(invite);
      return badRequest("Invite has reached its usage limit");
    }

    return null;
  }

  private boolean isAlreadyMember(LocalGroup group, User user) {
    return group.getMembers().stream()
      .anyMatch(u -> u.getId().equals(user.getId()));
  }

  private void addMemberAndUpdateInvite(LocalGroup group, LocalGroupInvite invite, User user) {
    group.getMembers().add(user);
    invite.setUses(invite.getUses() + 1);

    if (invite.getUses() >= invite.getMaxUses()) {
      inviteRepository.delete(invite);
    } else {
      inviteRepository.save(invite);
    }

    groupRepository.save(group);
  }

  private void notifyInviter(LocalGroupInvite invite, User newMember, UUID groupId) {
    userRepository.findByEmail(invite.getInviterEmail())
      .ifPresent(inviter ->
        notificationService.sendLocalGroupJoinNotification(newMember, inviter, groupId)
      );
  }

  private ApiResponse<?> badRequest(String message) {
    return new ApiResponse<>(400, message, null);
  }

  private ApiResponse<?> notFound() {
    return new ApiResponse<>(404, "User not found", null);
  }

  private ApiResponse<?> success(LocalGroup group) {
    LocalGroupJoinResponseDTO dto = new LocalGroupJoinResponseDTO(
      group.getId(),
      group.getName(),
      group.getDescription()
    );
    return new ApiResponse<>(200, "User joined local group successfully", dto);
  }



  @Override
  public ApiResponse<List<LocalGroupInviteResponseDTO>> getGroupInvites(UUID groupId) {
    List<LocalGroupInviteResponseDTO> invites = inviteRepository.findAll().stream()
            .filter(invite -> invite.getLocalGroup().getId().equals(groupId))
            .map(invite -> LocalGroupInviteResponseDTO.builder()
                    .inviteCode(invite.getInviteCode())
                    .inviteLink("https://codewithketan.me.localgroup/invite/" + groupId + "/" + invite.getInviteCode())
                    .groupId(groupId)
                    .inviterEmail(invite.getInviterEmail())
                    .maxUses(invite.getMaxUses())
                    .uses(invite.getUses())
                    .expiresAt(invite.getExpiresAt())
                    .status(invite.getExpiresAt().isBefore(LocalDateTime.now()) ? "EXPIRED" : "ACTIVE")
                    .build())
            .collect(Collectors.toList());

    return new ApiResponse<>(200, "Invites fetched successfully", invites);
  }

  @Override
  public ApiResponse<String> revokeInvite(String inviteCode) {
    Optional<LocalGroupInvite> optionalInvite = inviteRepository.findByInviteCode(inviteCode);

    if (optionalInvite.isEmpty()) {
      return new ApiResponse<>(400, "Invite not found", null);
    }

    inviteRepository.delete(optionalInvite.get());
    return new ApiResponse<>(200, "Invite revoked successfully", null);
  }

}
