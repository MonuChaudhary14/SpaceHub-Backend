package org.spacehub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.spacehub.service.JanusService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@RequiredArgsConstructor
public class VoiceRoomWebSocketController {

  private final JanusService janusService;
  private final SimpMessagingTemplate messagingTemplate;

  private final ConcurrentHashMap<String, String> userSessionMap = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> userHandleMap = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> userRoomMap = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Set<String>> roomParticipants = new ConcurrentHashMap<>();

  @MessageMapping("/register")
  public void registerUser(Map<String, String> payload) {
    String userId = payload.get("userId");
    String sessionId = payload.get("sessionId");
    String handleId = payload.get("handleId");
    String roomId = payload.get("roomId");

    if (userId == null || sessionId == null || handleId == null || roomId == null) return;

    userSessionMap.put(userId, sessionId);
    userHandleMap.put(userId, handleId);
    userRoomMap.put(userId, roomId);

    roomParticipants.computeIfAbsent(roomId, k -> new HashSet<>()).add(userId);

    Map<String, String> event = Map.of("type", "joined", "userId", userId);
    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/events", event);
  }

  @MessageMapping("/unregister")
  public void unregisterUser(Map<String, String> payload) {
    String userId = payload.get("userId");
    if (userId == null) return;

    String roomId = userRoomMap.remove(userId);
    userSessionMap.remove(userId);
    userHandleMap.remove(userId);

    if (roomId != null) {
      Set<String> participants = roomParticipants.getOrDefault(roomId, new HashSet<>());
      participants.remove(userId);

      Map<String, String> event = Map.of("type", "left", "userId", userId);
      messagingTemplate.convertAndSend("/topic/room/" + roomId + "/events", event);
    }
  }

  @MessageMapping("/offer")
  public void handleOffer(Map<String, String> payload) {
    String userId = payload.get("userId");
    String roomId = payload.get("roomId");
    String sdp = payload.get("sdp");
    if (userId == null || roomId == null || sdp == null) return;

    String sessionId = userSessionMap.get(userId);
    String handleId = userHandleMap.get(userId);

    if (sessionId == null || handleId == null) return;

    JsonNode janusResponse = janusService.sendOffer(sessionId, handleId, sdp);

    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/answer/" + userId, janusResponse);

    Set<String> participants = roomParticipants.getOrDefault(roomId, new HashSet<>());
    for (String participantId : participants) {
      if (!participantId.equals(userId)) {
        Map<String, Object> offerEvent = new HashMap<>();
        offerEvent.put("type", "offer");
        offerEvent.put("from", userId);
        offerEvent.put("roomId", roomId);
        offerEvent.put("sdp", sdp);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/offer/" + participantId, offerEvent);
      }
    }

    startJanusPoller(userId, sessionId, roomId);
  }

  @MessageMapping("/answer")
  public void handleAnswer(Map<String, String> payload) {
    String fromUser = payload.get("userId");
    String roomId = payload.get("roomId");
    String sdp = payload.get("sdp");
    String targetUser = payload.get("targetUserId");

    if (roomId == null || sdp == null || targetUser == null) return;

    Map<String, Object> answerEvent = new HashMap<>();
    answerEvent.put("type", "answer");
    answerEvent.put("from", fromUser);
    answerEvent.put("roomId", roomId);
    answerEvent.put("sdp", sdp);

    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/answer/" + targetUser, answerEvent);
  }

  @MessageMapping("/ice")
  public void handleIceCandidate(Map<String, Object> payload) {
    String userId = (String) payload.get("userId");
    String roomId = (String) payload.get("roomId");
    Object candidateObj = payload.get("candidate");
    if (userId == null || roomId == null || candidateObj == null) return;

    String sessionId = userSessionMap.get(userId);
    String handleId = userHandleMap.get(userId);
    if (sessionId == null || handleId == null) return;

    janusService.sendIce(sessionId, handleId, candidateObj);

    Set<String> participants = roomParticipants.getOrDefault(roomId, new HashSet<>());
    for (String participantId : participants) {
      if (!participantId.equals(userId)) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "ice");
        event.put("from", userId);
        event.put("candidate", candidateObj);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/ice/" + participantId, event);
      }
    }
  }

  @MessageMapping("/mute")
  public void handleMute(Map<String, String> payload) {
    String userId = payload.get("userId");
    String roomId = payload.get("roomId");
    String action = payload.get("action");

    if (userId == null || roomId == null || action == null) return;

    String sessionId = userSessionMap.get(userId);
    String handleId = userHandleMap.get(userId);
    if (sessionId == null || handleId == null) return;

    boolean mute = action.equalsIgnoreCase("mute");
    janusService.setMute(sessionId, handleId, mute);

    Map<String, Object> event = new HashMap<>();
    event.put("type", mute ? "muted" : "unmuted");
    event.put("userId", userId);

    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/events", event);
  }

  private void startJanusPoller(String userId, String sessionId, String roomId) {
    new Thread(() -> {
      try {
        for (int i = 0; i < 20; i++) {
          JsonNode evt = janusService.fetchSessionEvents(sessionId);
          if (evt != null) {
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/janus/" + userId, evt);
          }
          Thread.sleep(300);
        }
      } catch (InterruptedException ignored) {}
    }, "janus-poller-" + userId).start();
  }

}
