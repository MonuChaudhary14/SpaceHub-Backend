package org.spacehub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
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

  private final JanusService janusService;
  private final SimpMessagingTemplate messagingTemplate;

  private final ConcurrentHashMap<String, String> userSessionMap = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> userHandleMap = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> userRoomMap = new ConcurrentHashMap<>();

  @MessageMapping("/unregister")
  public void unregisterUser(Map<String, String> payload) {
    String username = payload.get("username");
    if (username == null) return;

    String roomId = userRoomMap.remove(username);
    userSessionMap.remove(username);
    userHandleMap.remove(username);

    if (roomId != null) {
      Map<String, String> event = Map.of("type", "left", "username", username);
      messagingTemplate.convertAndSend("/topic/room/" + roomId + "/events", event);
    }
  }

  @MessageMapping("/offer")
  public void handleOffer(Map<String, String> payload) {
    String username = payload.get("username");
    String roomId = payload.get("roomId");
    String sdp = payload.get("sdp");
    if (username == null || roomId == null || sdp == null) return;

    String sessionId = userSessionMap.get(username);
    String handleId = userHandleMap.get(username);
    if (sessionId == null || handleId == null) {
      Map<String, String> error = Map.of("type", "error", "message", "user not registered");
      messagingTemplate.convertAndSend("/topic/room/" + roomId + "/answer/" + username, error);
      return;
    }

    JsonNode janusResponse = janusService.sendOffer(sessionId, handleId, sdp);
    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/answer/" + username, janusResponse);

    final String pollSessionId = sessionId;
    final String pollRoomId = roomId;
    final String pollUserId = username;

    new Thread(() -> {
      try {
        for (int i = 0; i < 20; i++) {
          JsonNode evt = janusService.fetchSessionEvents(pollSessionId);
          if (evt != null) {
            if (evt.isArray()) {
              for (JsonNode n : evt) {
                messagingTemplate.convertAndSend("/topic/room/" + pollRoomId + "/answer/" +
                        pollUserId, n);
              }
            } else {
              messagingTemplate.convertAndSend("/topic/room/" + pollRoomId + "/answer/" +
                      pollUserId, evt);
            }
          }
          Thread.sleep(300);
        }
      } catch (InterruptedException ignored) {}
    }, "janus-poller-" + username + "-" + System.currentTimeMillis()).start();
  }

  @MessageMapping("/ice")
  public void handleIceCandidate(Map<String, Object> payload) {
    System.out.println("handleIce received: " + payload);
    String username  = (String) payload.get("userId");
    String roomId = (String) payload.get("roomId");
    Object candidateObj = payload.get("candidate");

    if (username  == null || roomId == null || candidateObj == null) {
      System.out.println("handleIce: missing fields");
      return;
    }

    String sessionId = userSessionMap.get(username );
    String handleId = userHandleMap.get(username );
    if (sessionId == null || handleId == null) {
      System.out.println("Logs for errror -> User Id:" + username);
      return;
    }
    janusService.sendIce(sessionId, handleId, candidateObj);
  }

  @MessageMapping("/mute")
  public void handleMute(Map<String, String> payload) {
    String username = payload.get("username");
    String roomId = payload.get("roomId");
    String action = payload.get("action");

    if (username == null || roomId == null || action == null) {
      return;
    }

    String sessionId = userSessionMap.get(username);
    String handleId = userHandleMap.get(username);
    if (sessionId == null || handleId == null) {
      return;
    }

    boolean mute = action.equalsIgnoreCase("mute");
    janusService.setMute(sessionId, handleId, mute);

    Map<String, Object> event = new HashMap<>();
    String type;
    if (mute) {
      type = "muted";
    } else {
      type = "unmuted";
    }
    event.put("type", type);
    event.put("userId", username);

    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/events", event);
  }

}