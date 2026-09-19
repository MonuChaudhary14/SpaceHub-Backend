package org.spacehub.controller.Presence;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.presence.HeartbeatRequest;
import org.spacehub.DTO.presence.OnlineUsersDTO;
import org.spacehub.DTO.presence.PresenceMessage;
import org.spacehub.DTO.presence.UserPresenceDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.Interface.IPresenceService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/presence")
public class PresenceController {

  private final IPresenceService presenceService;

  @PostMapping("/heartbeat")
  public ApiResponse<UserPresenceDTO> heartbeat(@RequestBody HeartbeatRequest request) {
    if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
      return new ApiResponse<>(400, "Email is required for heartbeat", null);
    }
    presenceService.recordHeartbeat(request.getEmail(), request.getCommunityId());
    UserPresenceDTO presence = presenceService.getUserPresence(request.getEmail());
    return new ApiResponse<>(200, "Heartbeat recorded successfully", presence);
  }

  @GetMapping("/community/{communityId}")
  public ApiResponse<OnlineUsersDTO> getCommunityOnlineUsers(@PathVariable Long communityId) {
    List<String> onlineEmails = presenceService.getCommunityOnlineUsers(communityId);
    return new ApiResponse<>(200, "Fetched community online users", new OnlineUsersDTO(communityId, onlineEmails));
  }

  @PostMapping("/batch")
  public ApiResponse<Map<String, String>> getUsersPresence(@RequestBody List<String> emails) {
    Map<String, String> statuses = presenceService.getUsersPresence(emails);
    return new ApiResponse<>(200, "Fetched user presence statuses", statuses);
  }

  @GetMapping("/user/{email}")
  public ApiResponse<UserPresenceDTO> getUserPresence(@PathVariable String email) {
    UserPresenceDTO dto = presenceService.getUserPresence(email);
    return new ApiResponse<>(200, "Fetched user presence", dto);
  }

  @MessageMapping("/presence/enter")
  public void enter(PresenceMessage message, SimpMessageHeaderAccessor headerAccessor) {
    String sessionId = headerAccessor.getSessionId();
    presenceService.userConnected(sessionId, message.getCommunityId(), message.getEmail());
  }

  @MessageMapping("/presence/leave")
  public void leave(SimpMessageHeaderAccessor headerAccessor) {
    String sessionId = headerAccessor.getSessionId();
    presenceService.userLeft(sessionId);
  }
}
