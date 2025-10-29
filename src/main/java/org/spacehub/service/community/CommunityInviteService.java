package org.spacehub.service.community;

import org.spacehub.DTO.Community.CommunityInviteAcceptDTO;
import org.spacehub.DTO.Community.CommunityInviteRequestDTO;
import org.spacehub.DTO.Community.CommunityInviteResponseDTO;
import org.spacehub.DTO.Community.CommunityJoinResponseDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.Community.*;
import org.spacehub.entities.User.User;
import org.spacehub.repository.ChatRoom.ChatRoomRepository;
import org.spacehub.repository.UserRepository;
import org.spacehub.repository.community.CommunityInviteRepository;
import org.spacehub.repository.community.CommunityRepository;
import org.spacehub.repository.community.CommunityUserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CommunityInviteService {

  private final CommunityInviteRepository inviteRepository;
  private final CommunityRepository communityRepository;
  private final CommunityUserRepository communityUserRepository;
  private final UserRepository userRepository;

  public CommunityInviteService(
          CommunityInviteRepository inviteRepository,
          CommunityRepository communityRepository,
          CommunityUserRepository communityUserRepository,
          UserRepository userRepository) {
    this.inviteRepository = inviteRepository;
    this.communityRepository = communityRepository;
    this.communityUserRepository = communityUserRepository;
    this.userRepository = userRepository;
  }

  private String generateInviteCode() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  public ApiResponse<CommunityInviteResponseDTO> createInvite(Long communityId, CommunityInviteRequestDTO request) {

    Optional<Community> optionalCommunity = communityRepository.findById(communityId);
    if (optionalCommunity.isEmpty()) {
      return new ApiResponse<>(404, "Community not found", null);
    }

    CommunityInvite invite = CommunityInvite.builder()
            .communityId(communityId)
            .inviterId(request.getInviterId())
            .email(request.getEmail())
            .maxUses(request.getMaxUses())
            .inviteCode(generateInviteCode())
            .expiresAt(LocalDateTime.now().plusHours(request.getExpiresInHours()))
            .status(InviteStatus.ACTIVE)
            .build();

    inviteRepository.save(invite);

    CommunityInviteResponseDTO response = CommunityInviteResponseDTO.builder()
            .inviteCode(invite.getInviteCode())
            .inviteLink("https://codewithketan.me/invite/" + communityId + "?code=" + invite.getInviteCode())
            .communityId(communityId)
            .email(invite.getEmail())
            .maxUses(invite.getMaxUses())
            .uses(invite.getUses())
            .expiresAt(invite.getExpiresAt())
            .status(invite.getStatus().name())
            .build();

    return new ApiResponse<>(200, "Invite created successfully", response);
  }


  public ApiResponse<CommunityJoinResponseDTO> acceptInvite(CommunityInviteAcceptDTO request) {

    Optional<CommunityInvite> optionalInvite = inviteRepository.findByInviteCode(request.getInviteCode());
    if (optionalInvite.isEmpty()) {
      return new ApiResponse<>(400, "Invalid invite link", null);
    }

    CommunityInvite invite = optionalInvite.get();

    if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
      invite.setStatus(InviteStatus.EXPIRED);
      inviteRepository.save(invite);
      return new ApiResponse<>(400, "Invite link has expired", null);
    }

    if (invite.getUses() >= invite.getMaxUses()) {
      invite.setStatus(InviteStatus.USED);
      inviteRepository.save(invite);
      return new ApiResponse<>(400, "Invite already used", null);
    }

    Optional<Community> optionalCommunity = communityRepository.findById(invite.getCommunityId());
    if (optionalCommunity.isEmpty()) {
      return new ApiResponse<>(404, "Community not found", null);
    }
    Community community = optionalCommunity.get();

    Optional<User> userOpt = userRepository.findById(request.getUserId());
    if (userOpt.isEmpty()) {
      return new ApiResponse<>(404, "User not found", null);
    }
    User user = userOpt.get();

    boolean alreadyMember = communityUserRepository.findByCommunityId(community.getId())
            .stream().anyMatch(cu -> cu.getUser().getId().equals(user.getId()));

    if (alreadyMember) {
      return new ApiResponse<>(400, "User is already a member of this community", null);
    }

    CommunityUser communityUser = new CommunityUser();
    communityUser.setCommunity(community);
    communityUser.setUser(user);
    communityUser.setRole(Role.MEMBER);
    communityUserRepository.save(communityUser);

    invite.setUses(invite.getUses() + 1);
    if (invite.getUses() >= invite.getMaxUses()) {
      invite.setStatus(InviteStatus.USED);
    }
    inviteRepository.save(invite);

    return new ApiResponse<>(200, "User successfully joined community: " + community.getName(), null);
  }

  public ApiResponse<List<CommunityInviteResponseDTO>> getCommunityInvites(Long communityId) {
    List<CommunityInviteResponseDTO> invites = inviteRepository.findAll()
            .stream().filter(invite -> invite.getCommunityId().equals(communityId))
            .map(invite -> CommunityInviteResponseDTO.builder()
                    .inviteCode(invite.getInviteCode())
                    .inviteLink("https://yourdomain.com/invite/" + communityId + "?code=" + invite.getInviteCode())
                    .communityId(invite.getCommunityId())
                    .email(invite.getEmail())
                    .maxUses(invite.getMaxUses())
                    .uses(invite.getUses())
                    .expiresAt(invite.getExpiresAt())
                    .status(invite.getStatus().name())
                    .build())
            .toList();

    return new ApiResponse<>(200, "Invites fetched successfully", invites);
  }

  public ApiResponse<String> revokeInvite(String inviteCode) {

    Optional<CommunityInvite> optionalInvite = inviteRepository.findByInviteCode(inviteCode);
    if (optionalInvite.isEmpty()) {
      return new ApiResponse<>(400, "Invite not found", null);
    }

    inviteRepository.delete(optionalInvite.get());
    return new ApiResponse<>(200, "Invite revoked successfully", null);
  }

}
