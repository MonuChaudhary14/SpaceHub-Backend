package org.spacehub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.spacehub.service.JanusVideoService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class VideoRoomWebSocketController {

    private final JanusVideoService janusVideoService;
    private final SimpMessagingTemplate messagingTemplate;

    public VideoRoomWebSocketController(JanusVideoService janusVideoService, SimpMessagingTemplate messagingTemplate) {
        this.janusVideoService = janusVideoService;
        this.messagingTemplate = messagingTemplate;
    }

    private final ConcurrentHashMap<String, String> userSessionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userHandleMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userRoomMap = new ConcurrentHashMap<>();

    @MessageMapping("/video/register")
    public void registerUser(Map<String, String> payload) {
        String userId = payload.get("userId");
        String sessionId = payload.get("sessionId");
        String handleId = payload.get("handleId");
        String roomId = payload.get("roomId");

        if (userId == null || sessionId == null || handleId == null || roomId == null) return;

        userSessionMap.put(userId, sessionId);
        userHandleMap.put(userId, handleId);
        userRoomMap.put(userId, roomId);

        Map<String, Object> event = Map.of("type", "joined", "userId", userId);
        messagingTemplate.convertAndSend("/topic/video/" + roomId + "/events", event);
    }

    @MessageMapping("/video/unregister")
    public void unregisterUser(Map<String, String> payload) {
        String userId = payload.get("userId");
        if (userId == null) return;

        String roomId = userRoomMap.remove(userId);
        userSessionMap.remove(userId);
        userHandleMap.remove(userId);

        if (roomId != null) {
            Map<String, Object> event = Map.of("type", "left", "userId", userId);
            messagingTemplate.convertAndSend("/topic/video/" + roomId + "/events", event);
        }
    }

    @MessageMapping("/video/offer")
    public void handleOffer(Map<String, String> payload) {
        String userId = payload.get("userId");
        String roomId = payload.get("roomId");
        String sdp = payload.get("sdp");

        if (userId == null || roomId == null || sdp == null) return;

        String sessionId = userSessionMap.get(userId);
        String handleId = userHandleMap.get(userId);

        if (sessionId == null || handleId == null) {
            Map<String, String> error = Map.of("type", "error", "message", "User not registered");
            messagingTemplate.convertAndSend("/topic/video/" + roomId + "/answer/" + userId, error);
            return;
        }

        JsonNode response = janusVideoService.publishOwnFeed(sessionId, handleId, sdp);
        messagingTemplate.convertAndSend("/topic/video/" + roomId + "/answer/" + userId, response);
    }

    @MessageMapping("/video/ice")
    public void handleIceCandidate(Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        String roomId = (String) payload.get("roomId");
        Object candidateObj = payload.get("candidate");

        if (userId == null || roomId == null || candidateObj == null) return;

        String sessionId = userSessionMap.get(userId);
        String handleId = userHandleMap.get(userId);
        if (sessionId == null || handleId == null) return;

        String url = String.format("%s/%s/%s", janusVideoService.getJanusUrl(), sessionId, handleId);

        Map<String, Object> request = Map.of(
                "janus", "trickle","candidate", candidateObj,"transaction", java.util.UUID.randomUUID().toString());

        try {
            new RestTemplate().postForEntity(url, request, JsonNode.class);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @MessageMapping("/video/mute")
    public void handleVideoToggle(Map<String, String> payload) {
        String userId = payload.get("userId");
        String roomId = payload.get("roomId");
        String action = payload.get("action");

        if (userId == null || roomId == null || action == null) return;

        Map<String, Object> event = new HashMap<>();
        event.put("userId", userId);
        event.put("type", action.equalsIgnoreCase("mute") ? "muted" : "unmuted");

        messagingTemplate.convertAndSend("/topic/video/" + roomId + "/events", event);
    }

}
