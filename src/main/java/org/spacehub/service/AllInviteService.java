package org.spacehub.service;

import org.spacehub.DTO.Community.CommunityInviteAcceptDTO;
import org.spacehub.DTO.InviteAcceptDTO;
import org.spacehub.DTO.LocalGroup.LocalGroupInviteAcceptDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.LocalRoom.LocalGroupInviteService;
import org.spacehub.service.community.CommunityInviteService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AllInviteService {

  private final CommunityInviteService communityInviteService;
  private final LocalGroupInviteService localGroupInviteService;

  public AllInviteService(CommunityInviteService communityInviteService, LocalGroupInviteService localGroupInviteService) {
    this.communityInviteService = communityInviteService;
    this.localGroupInviteService = localGroupInviteService;
  }

  public ApiResponse<?> acceptInvite(InviteAcceptDTO req) {
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

    String t = req.getType().trim().toLowerCase();

    return switch (t) {
      case "community", "comm", "c" -> {
        CommunityInviteAcceptDTO cDto = CommunityInviteAcceptDTO.builder()
          .communityId(req.getId())
          .inviteCode(req.getInviteCode())
          .acceptorEmail(req.getAcceptorEmail())
          .build();
        yield communityInviteService.acceptInvite(cDto);
      }
      case "local_group", "localgroup", "group", "g" -> {
        LocalGroupInviteAcceptDTO lDto = LocalGroupInviteAcceptDTO.builder()
          .groupId(req.getId())
          .inviteCode(req.getInviteCode())
          .acceptorEmail(req.getAcceptorEmail())
          .build();
        yield localGroupInviteService.acceptInvite(lDto);
      }
      default -> new ApiResponse<>(400, "Unknown invite type: " + req.getType() +
        " (expected 'community' or 'local_group')", null);
    };
  }

}
