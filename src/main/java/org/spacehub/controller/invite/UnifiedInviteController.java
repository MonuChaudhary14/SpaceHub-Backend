package org.spacehub.controller.invite;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.invite.InviteAcceptDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.invite.AllInviteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/invites")
@RequiredArgsConstructor
public class UnifiedInviteController {

  private final AllInviteService allInviteService;

  @PostMapping("/accept")
  public ResponseEntity<ApiResponse<?>> acceptInvite(@RequestBody InviteAcceptDTO request) {
    ApiResponse<?> response = allInviteService.acceptInvite(request);
    return ResponseEntity.status(response.getStatus()).body(response);
  }
}
