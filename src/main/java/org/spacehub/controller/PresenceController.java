package org.spacehub.controller;

import org.spacehub.DTO.presence.PresenceMessage;
import org.spacehub.service.PresenceService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class PresenceController {

  private final PresenceService presenceService;

  public PresenceController(PresenceService presenceService) {
    this.presenceService = presenceService;
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
