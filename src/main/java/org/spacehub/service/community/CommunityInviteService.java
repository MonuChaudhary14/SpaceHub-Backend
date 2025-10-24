package org.spacehub.service.community;

import org.spacehub.DTO.Community.CommunityInviteAcceptDTO;
import org.spacehub.DTO.Community.CommunityInviteRequestDTO;
import org.spacehub.DTO.Community.CommunityInviteResponseDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.Community.CommunityInvite;
import org.spacehub.entities.Community.InviteStatus;
import org.spacehub.repository.commnunity.CommunityInviteRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Random;

@Service
public class CommunityInviteService {

    private final CommunityInviteRepository inviteRepository;

    public CommunityInviteService(CommunityInviteRepository inviteRepository) {
        this.inviteRepository = inviteRepository;
    }

    private String generateInviteCode() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public ApiResponse<CommunityInviteResponseDTO> createInvite(UUID communityId, CommunityInviteRequestDTO request) {
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
                .inviteLink("https:///invite/" + invite.getInviteCode())
                .communityId(communityId)
                .email(invite.getEmail())
                .maxUses(invite.getMaxUses())
                .uses(invite.getUses())
                .expiresAt(invite.getExpiresAt())
                .status(invite.getStatus().name())
                .build();

        return new ApiResponse<>(200, "Invite created successfully", response);
    }

    public ApiResponse<String> acceptInvite(CommunityInviteAcceptDTO request) {

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

        invite.setUses(invite.getUses() + 1);
        if (invite.getUses() >= invite.getMaxUses()) {
            invite.setStatus(InviteStatus.USED);
        }
        inviteRepository.save(invite);

        return new ApiResponse<>(200, "User successfully joined the community", null);
    }

    public ApiResponse<List<CommunityInviteResponseDTO>> getCommunityInvites(UUID communityId) {

        List<CommunityInviteResponseDTO> invites = inviteRepository.findAll()
                .stream()
                .filter(invite -> invite.getCommunityId().equals(communityId))
                .map(invite -> CommunityInviteResponseDTO.builder()
                        .inviteCode(invite.getInviteCode())
                        .inviteLink("https:///invite/" + invite.getInviteCode())
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

        Optional<CommunityInvite> inviteOpt = inviteRepository.findByInviteCode(inviteCode);

        if (inviteOpt.isEmpty()) {
            return new ApiResponse<>(400, "Invite not found", null);
        }

        inviteRepository.delete(inviteOpt.get());
        return new ApiResponse<>(200, "Invite revoked successfully", null);
    }

}
