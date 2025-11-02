package org.spacehub.service.community.CommunityInterfaces;

import org.spacehub.DTO.Community.CommunityInviteAcceptDTO;
import org.spacehub.DTO.Community.CommunityInviteRequestDTO;
import org.spacehub.DTO.Community.CommunityInviteResponseDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;

import java.util.List;
import java.util.UUID;

public interface ICommunityInviteService {

  ApiResponse<CommunityInviteResponseDTO> createInvite(UUID communityId, CommunityInviteRequestDTO request);

  ApiResponse<?> acceptInvite(CommunityInviteAcceptDTO request);

  ApiResponse<List<CommunityInviteResponseDTO>> getCommunityInvites(UUID communityId);

  ApiResponse<String> revokeInvite(String inviteCode);
}

