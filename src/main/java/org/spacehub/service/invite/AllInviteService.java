package org.spacehub.service.invite;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.Community.CommunityInviteAcceptDTO;
import org.spacehub.DTO.invite.InviteAcceptDTO;
import org.spacehub.DTO.LocalGroup.LocalGroupInviteAcceptDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.LocalRoom.LocalGroupInviteService;
import org.spacehub.service.community.CommunityInviteService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AllInviteService {

  private final CommunityInviteService communityInviteService;
  private final LocalGroupInviteService localGroupInviteService;

  public ApiResponse<?> acceptInvite(InviteAcceptDTO req) {

    ApiResponse<?> validation = validateInviteRequest(req);
    if (validation != null) {
      return validation;
    }

    return dispatchInvite(req);
  }

  private ApiResponse<?> validateInviteRequest(InviteAcceptDTO req) {

    if (req == null) {
      return new ApiResponse<>(400, "Request body is required", null);
    }
    if (!StringUtils.hasText(req.getType())) {
      return new ApiResponse<>(400, "type is required (community | local_group)", null);
    }
    if (req.getId() == null) {
      return new ApiResponse<>(400, "id (communityId or groupId) is required", null);
    }
    if (!StringUtils.hasText(req.getInviteCode())) {
      return new ApiResponse<>(400, "inviteCode is required", null);
    }
    if (!StringUtils.hasText(req.getAcceptorEmail())) {
      return new ApiResponse<>(400, "acceptorEmail is required", null);
    }

    return null;
  }

  private ApiResponse<?> dispatchInvite(InviteAcceptDTO req) {

    String t = req.getType().trim().toLowerCase();

    return switch (t) {

      case "community", "comm", "c" -> handleCommunityAccept(req);

      case "local_group", "localgroup", "group", "g" -> handleLocalGroupAccept(req);

      default -> new ApiResponse<>(400,
        "Unknown invite type: " + req.getType() +
          " (expected 'community' or 'local_group')",
        null);
    };
  }

  private ApiResponse<?> handleCommunityAccept(InviteAcceptDTO req) {
    CommunityInviteAcceptDTO dto = CommunityInviteAcceptDTO.builder()
      .communityId(req.getId())
      .inviteCode(req.getInviteCode())
      .acceptorEmail(req.getAcceptorEmail())
      .build();
    return communityInviteService.acceptInvite(dto);
  }

  private ApiResponse<?> handleLocalGroupAccept(InviteAcceptDTO req) {
    LocalGroupInviteAcceptDTO dto = LocalGroupInviteAcceptDTO.builder()
      .groupId(req.getId())
      .inviteCode(req.getInviteCode())
      .acceptorEmail(req.getAcceptorEmail())
      .build();
    return localGroupInviteService.acceptInvite(dto);
  }

}
