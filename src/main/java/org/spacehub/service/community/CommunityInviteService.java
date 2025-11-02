package org.spacehub.service.community;

import org.spacehub.DTO.Community.CommunityInviteAcceptDTO;
import org.spacehub.DTO.Community.CommunityInviteRequestDTO;
import org.spacehub.DTO.Community.CommunityInviteResponseDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.Community.*;
import org.spacehub.entities.User.User;
import org.spacehub.repository.UserRepository;
import org.spacehub.repository.community.CommunityInviteRepository;
import org.spacehub.repository.community.CommunityRepository;
import org.spacehub.repository.community.CommunityUserRepository;
import org.spacehub.service.community.CommunityInterfaces.ICommunityInviteService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CommunityInviteService implements ICommunityInviteService {

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

  @Override
  public ApiResponse<CommunityInviteResponseDTO> createInvite(UUID communityId, CommunityInviteRequestDTO request) {
    Optional<Community> optionalCommunity = communityRepository.findById(communityId);
    if (optionalCommunity.isEmpty()) {
      return new ApiResponse<>(404, "Community not found", null);
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

    String inviteLink = String.format("https://codewithketan.me/invite/%s/%s", communityId, invite.getInviteCode());

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

    String rawCode = request.getInviteCode();
    UUID communityId = request.getCommunityId();
    String acceptorEmail = request.getAcceptorEmail();

    if (acceptorEmail == null || acceptorEmail.isBlank()) {
      return new ApiResponse<>(400, "Acceptor email is required", null);
    }

    String inviteCode = rawCode.contains("/") ? rawCode.substring(rawCode.lastIndexOf("/") + 1) : rawCode;

    Optional<CommunityInvite> inviteOpt = inviteRepository.findByInviteCode(inviteCode);
    if (inviteOpt.isEmpty()) {
      return new ApiResponse<>(400, "Invalid invite link", null);
    }

    CommunityInvite invite = inviteOpt.get();

    if (!invite.getCommunityId().equals(communityId)) {
      return new ApiResponse<>(400, "Invite does not belong to this community", null);
    }

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

    invite.setUses(invite.getUses() + 1);
    if (invite.getUses() >= invite.getMaxUses()) {
      invite.setStatus(InviteStatus.USED);
    }
    inviteRepository.save(invite);

    Optional<Community> optionalCommunity = communityRepository.findById(communityId);
    if (optionalCommunity.isEmpty()) {
      return new ApiResponse<>(404, "Community not found", null);
    }

    Optional<User> optionalUser = userRepository.findByEmail(acceptorEmail);
    if (optionalUser.isEmpty()) {
      return new ApiResponse<>(404, "User not found", null);
    }

    Community community = optionalCommunity.get();
    User user = optionalUser.get();

    boolean alreadyMember = community.getMembers().stream()
            .anyMatch(u -> u.getId().equals(user.getId()));
    if (alreadyMember) {
      return new ApiResponse<>(400, "User is already a member of this community", community);
    }

    community.getMembers().add(user);
    communityRepository.save(community);

    CommunityUser communityUser = new CommunityUser();
    communityUser.setCommunity(community);
    communityUser.setUser(user);
    communityUser.setRole(Role.MEMBER);
    communityUser.setJoinDate(LocalDateTime.now());
    communityUserRepository.save(communityUser);

    return new ApiResponse<>(200, "User joined community successfully", community);
  }

  @Override
  public ApiResponse<List<CommunityInviteResponseDTO>> getCommunityInvites(UUID communityId) {
    List<CommunityInviteResponseDTO> invites = inviteRepository.findAll().stream()
            .filter(invite -> invite.getCommunityId().equals(communityId))
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

    inviteRepository.delete(optionalInvite.get());
    return new ApiResponse<>(200, "Invite revoked successfully", null);
  }

}
