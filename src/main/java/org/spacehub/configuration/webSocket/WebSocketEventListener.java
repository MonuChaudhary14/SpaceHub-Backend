package org.spacehub.configuration.webSocket;

import org.spacehub.service.Interface.IPresenceService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketEventListener {

  private final IPresenceService presenceService;

  public WebSocketEventListener(IPresenceService presenceService) {
    this.presenceService = presenceService;
  }

  @EventListener
  public void handleSessionDisconnect(SessionDisconnectEvent event) {
    StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
    String sessionId = sha.getSessionId();
    presenceService.userDisconnected(sessionId);
  }
}
