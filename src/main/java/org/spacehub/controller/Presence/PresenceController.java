package org.spacehub.controller.Presence;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.presence.PresenceMessage;
import org.spacehub.service.Interface.IPresenceService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@RequiredArgsConstructor
@Controller
public class PresenceController {

  private final IPresenceService presenceService;

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
