package org.spacehub.controller.invite;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.invite.InviteAcceptDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.invite.UnifiedInviteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invites")
@RequiredArgsConstructor
public class UnifiedInviteController {

  private final UnifiedInviteService unifiedInviteService;

  @PostMapping("/accept")
  public ResponseEntity<ApiResponse<?>> acceptInvite(@RequestBody InviteAcceptDTO request) {
    ApiResponse<?> response = unifiedInviteService.acceptInvite(request);
    return ResponseEntity.status(response.getStatus()).body(response);
  }
}
