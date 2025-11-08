package org.spacehub.controller.localgroup;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.LocalGroup.LocalGroupInviteAcceptDTO;
import org.spacehub.DTO.LocalGroup.LocalGroupInviteRequestDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.DTO.LocalGroup.LocalGroupInviteResponseDTO;
import org.spacehub.service.Interface.ILocalGroupInviteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/localgroup/invites")
@RequiredArgsConstructor
public class LocalGroupInviteController {

  private final ILocalGroupInviteService inviteService;

  @PostMapping("/create/{groupId}")
  public ResponseEntity<ApiResponse<LocalGroupInviteResponseDTO>> createInvite(
    @PathVariable UUID groupId, @RequestBody LocalGroupInviteRequestDTO request) {
    ApiResponse<LocalGroupInviteResponseDTO> response = inviteService.createInvite(groupId, request);
    return ResponseEntity.status(response.getStatus()).body(response);
  }

  @PostMapping("/accept")
  public ResponseEntity<ApiResponse<?>> acceptInvite(@RequestBody LocalGroupInviteAcceptDTO request) {
    ApiResponse<?> response = inviteService.acceptInvite(request);
    return ResponseEntity.status(response.getStatus()).body(response);
  }

  @GetMapping("/list/{groupId}")
  public ResponseEntity<ApiResponse<List<LocalGroupInviteResponseDTO>>> getGroupInvites(@PathVariable UUID groupId) {
    ApiResponse<List<LocalGroupInviteResponseDTO>> response = inviteService.getGroupInvites(groupId);
    return ResponseEntity.status(response.getStatus()).body(response);
  }

  @DeleteMapping("/revoke/{inviteCode}")
  public ResponseEntity<ApiResponse<String>> revokeInvite(@PathVariable String inviteCode) {
    ApiResponse<String> response = inviteService.revokeInvite(inviteCode);
    return ResponseEntity.status(response.getStatus()).body(response);
  }

}
