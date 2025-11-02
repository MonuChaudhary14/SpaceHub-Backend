package org.spacehub.service;

import org.spacehub.DTO.LocalGroup.LocalGroupInviteAcceptDTO;
import org.spacehub.DTO.LocalGroup.LocalGroupInviteRequestDTO;
import org.spacehub.DTO.LocalGroup.LocalGroupInviteResponseDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.LocalGroup.LocalGroup;
import org.spacehub.entities.LocalGroup.LocalGroupInvite;
import org.spacehub.entities.User.User;
import org.spacehub.repository.UserRepository;
import org.spacehub.repository.localgroup.LocalGroupInviteRepository;
import org.spacehub.repository.localgroup.LocalGroupRepository;
import org.spacehub.service.Interface.ILocalGroupInviteService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LocalGroupInviteService implements ILocalGroupInviteService {

    private final LocalGroupInviteRepository inviteRepository;
    private final LocalGroupRepository groupRepository;
    private final UserRepository userRepository;

    public LocalGroupInviteService(LocalGroupInviteRepository inviteRepository, LocalGroupRepository groupRepository, UserRepository userRepository){
        this.inviteRepository = inviteRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
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

        String rawCode = request.getInviteCode();
        UUID groupId = request.getGroupId();
        String acceptorEmail = request.getAcceptorEmail();

        if (acceptorEmail == null || acceptorEmail.isBlank()) {
            return new ApiResponse<>(400, "Acceptor email is required", null);
        }

        String inviteCode = rawCode.contains("/") ? rawCode.substring(rawCode.lastIndexOf("/") + 1) : rawCode;

        Optional<LocalGroupInvite> inviteOpt = inviteRepository.findByInviteCode(inviteCode);
        if (inviteOpt.isEmpty()) {
            return new ApiResponse<>(400, "Invalid invite link", null);
        }

        LocalGroupInvite invite = inviteOpt.get();

        if (!invite.getLocalGroup().getId().equals(groupId)) {
            return new ApiResponse<>(400, "Invite does not belong to this local group", null);
        }

        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            inviteRepository.delete(invite);
            return new ApiResponse<>(400, "Invite link has expired", null);
        }

        if (invite.getUses() >= invite.getMaxUses()) {
            inviteRepository.delete(invite);
            return new ApiResponse<>(400, "Invite has reached its usage limit", null);
        }

        Optional<User> optionalUser = userRepository.findByEmail(acceptorEmail);
        if (optionalUser.isEmpty()) {
            return new ApiResponse<>(404, "User not found", null);
        }

        User user = optionalUser.get();
        LocalGroup group = invite.getLocalGroup();

        boolean alreadyMember = group.getMembers().stream()
                .anyMatch(u -> u.getId().equals(user.getId()));
        if (alreadyMember) {
            return new ApiResponse<>(400, "User is already a member of this local group", group);
        }

        group.getMembers().add(user);
        invite.setUses(invite.getUses() + 1);

        if (invite.getUses() >= invite.getMaxUses()) {
            inviteRepository.delete(invite);
        } else {
            inviteRepository.save(invite);
        }

        groupRepository.save(group);

        return new ApiResponse<>(200, "User joined local group successfully", group);
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
