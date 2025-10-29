//package org.spacehub.configuration;
//
//import org.spacehub.service.LocationService;
//import org.springframework.context.event.EventListener;
//import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
//import org.springframework.stereotype.Component;
//import org.springframework.web.socket.messaging.SessionDisconnectEvent;
//
//@Component
//public class WebSocketEventListener {
//
//  private final LocationService locationService;
//
//  public WebSocketEventListener(LocationService locationService) {
//    this.locationService = locationService;
//  }
//
//  @EventListener
//  public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
//    StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
//    if (headerAccessor.getUser() != null) {
//      String username = headerAccessor.getUser().getName();
//      locationService.removeUser(username);
//    }
//  }
//}
