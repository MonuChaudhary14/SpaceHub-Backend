////package org.spacehub.configuration;
////
////import org.spacehub.service.LocationService;
////import org.springframework.context.event.EventListener;
////import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
////import org.springframework.stereotype.Component;
////import org.springframework.web.socket.messaging.SessionDisconnectEvent;
////
////@Component
////public class WebSocketEventListener {
////
////  private final LocationService locationService;
////
////  public WebSocketEventListener(LocationService locationService) {
////    this.locationService = locationService;
////  }
////
////  @EventListener
////  public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
////    StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
////    if (headerAccessor.getUser() != null) {
////      String username = headerAccessor.getUser().getName();
////      locationService.removeUser(username);
////    }
////  }
////}
//package org.spacehub.configuration;
//
//import org.spacehub.service.Interface.IPresenceService;
//import org.springframework.context.event.EventListener;
//import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
//import org.springframework.stereotype.Component;
//import org.springframework.web.socket.messaging.SessionDisconnectEvent;
//
//@Component
//public class WebSocketEventListener {
//
//  private final IPresenceService presenceService;
//
//  public WebSocketEventListener(IPresenceService presenceService) {
//    this.presenceService = presenceService;
//  }
//
//  @EventListener
//  public void handleSessionDisconnect(SessionDisconnectEvent event) {
//    StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
//    String sessionId = sha.getSessionId();
//    presenceService.userDisconnected(sessionId);
//  }
//}
