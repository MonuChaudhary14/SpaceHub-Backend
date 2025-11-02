package org.spacehub.service.Interface;

import org.spacehub.DTO.LocalGroup.LocalGroupInviteAcceptDTO;
import org.spacehub.DTO.LocalGroup.LocalGroupInviteRequestDTO;
import org.spacehub.DTO.LocalGroup.LocalGroupInviteResponseDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;

import java.util.List;
import java.util.UUID;

public interface ILocalGroupInviteService {

    ApiResponse<LocalGroupInviteResponseDTO> createInvite(UUID groupId, LocalGroupInviteRequestDTO request);

    ApiResponse<?> acceptInvite(LocalGroupInviteAcceptDTO request);

    ApiResponse<List<LocalGroupInviteResponseDTO>> getGroupInvites(UUID groupId);

    ApiResponse<String> revokeInvite(String inviteCode);

}
