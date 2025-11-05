package org.spacehub.controller;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.service.JanusService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@RequiredArgsConstructor
public class VoiceRoomWebSocketController {

  private static final Logger logger = LoggerFactory.getLogger(VoiceRoomWebSocketController.class);

  private final JanusService janusService;
  private final SimpMessagingTemplate messagingTemplate;

  private final ConcurrentHashMap<String, String> userSessionMap = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> userHandleMap = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> userRoomMap = new ConcurrentHashMap<>();

  @MessageMapping("/register")
  public void registerUser(Map<String, String> payload) {
    String userId = payload.get("userId");
    String sessionId = payload.get("sessionId");
    String handleId = payload.get("handleId");
    String roomId = payload.get("roomId");

    if (userId == null || sessionId == null || handleId == null || roomId == null) {
      logger.warn("Invalid registration payload: {}", payload);
      return;
    }

    userSessionMap.put(userId, sessionId);
    userHandleMap.put(userId, handleId);
    userRoomMap.put(userId, roomId);

    logger.info("User {} registered for room {}. Starting event polling.", userId, roomId);

    janusService.startEventPolling(sessionId, janusEvent -> {
      try {
        long senderHandle = janusEvent.path("sender").asLong();
        if (senderHandle == 0 || senderHandle == Long.parseLong(handleId)) {
          messagingTemplate.convertAndSend("/topic/room/" + roomId + "/answer/" + userId, janusEvent);
        } else {
          janusEvent.path("janus").asText();
        }
      } catch (Exception e) {
        logger.error("Error forwarding Janus event: {}", e.getMessage());
      }
    });

    Map<String, String> event = Map.of("type", "joined", "userId", userId);
    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/events", event);
  }

  @MessageMapping("/unregister")
  public void unregisterUser(Map<String, String> payload) {
    String userId = payload.get("userId");
    if (userId == null) {
      return;
    }

    logger.info("User {} unregistering.", userId);
    String sessionId = userSessionMap.remove(userId);
    userHandleMap.remove(userId);
    String roomId = userRoomMap.remove(userId);

    if (sessionId != null) {
      janusService.stopEventPolling(sessionId);
    }

    if (roomId != null) {
      Map<String, String> event = Map.of("type", "left", "userId", userId);
      messagingTemplate.convertAndSend("/topic/room/" + roomId + "/events", event);
    }
  }

  @MessageMapping("/offer")
  public void handleOffer(Map<String, String> payload) {
    String userId = payload.get("userId");
    String sdp = payload.get("sdp");
    if (userId == null || sdp == null) return;

    String sessionId = userSessionMap.get(userId);
    String handleId = userHandleMap.get(userId);
    String roomId = userRoomMap.get(userId);

    if (sessionId == null || handleId == null || roomId == null) {
      logger.warn("Offer from unregistered user {} (or missing session/handle/room)", userId);
      return;
    }

    janusService.sendOffer(sessionId, handleId, sdp, userId, roomId, messagingTemplate);
  }

  @MessageMapping("/ice")
  public void handleIceCandidate(Map<String, Object> payload) {
    String userId = (String) payload.get("userId");
    Object candidateObj = payload.get("candidate");
    if (userId == null || candidateObj == null) {
      return;
    }

    String sessionId = userSessionMap.get(userId);
    String handleId = userHandleMap.get(userId);

    if (sessionId == null || handleId == null) {
      logger.warn("ICE from unregistered user {}", userId);
      return;
    }

    janusService.sendIce(sessionId, handleId, candidateObj);
  }

  @MessageMapping("/mute")
  public void handleMute(Map<String, String> payload) {
    String userId = payload.get("userId");
    String roomId = payload.get("roomId");
    String action = payload.get("action");
    if (userId == null || roomId == null || action == null) {
      return;
    }

    String sessionId = userSessionMap.get(userId);
    String handleId = userHandleMap.get(userId);
    if (sessionId == null || handleId == null) {
      return;
    }

    boolean mute = action.equalsIgnoreCase("mute");
    janusService.setMute(sessionId, handleId, mute);

    Map<String, Object> event = new HashMap<>();
    event.put("type", mute ? "muted" : "unmuted");
    event.put("userId", userId);
    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/events", event);
  }
}
