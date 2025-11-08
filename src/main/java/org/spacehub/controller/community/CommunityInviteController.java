package org.spacehub.controller.community;


import org.spacehub.DTO.Community.CommunityInviteAcceptDTO;
import org.spacehub.DTO.Community.CommunityInviteRequestDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.community.CommunityInterfaces.ICommunityInviteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/community/invites")
public class CommunityInviteController {

  private final ICommunityInviteService inviteService;

  public CommunityInviteController(ICommunityInviteService inviteService) {
    this.inviteService = inviteService;
  }

  @PostMapping("/{communityId}/create")
  public ResponseEntity<ApiResponse<?>> createInvite(@PathVariable UUID communityId, @RequestBody CommunityInviteRequestDTO request) {
    ApiResponse<?> response = inviteService.createInvite(communityId, request);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/accept")
  public ResponseEntity<ApiResponse<?>> acceptInvite(@RequestBody CommunityInviteAcceptDTO request) {
    ApiResponse<?> response = inviteService.acceptInvite(request);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{communityId}/all")
  public ResponseEntity<ApiResponse<?>> getInvites(@PathVariable UUID communityId) {
    ApiResponse<?> response = inviteService.getCommunityInvites(communityId);
    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{inviteCode}/revoke")
  public ResponseEntity<ApiResponse<?>> revokeInvite(@PathVariable String inviteCode) {
    ApiResponse<?> response = inviteService.revokeInvite(inviteCode);
    return ResponseEntity.ok(response);
  }

}
