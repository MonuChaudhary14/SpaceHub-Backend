package org.spacehub.controller.VideoRoom;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.service.VideoRoom.JanusVideoService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@RequiredArgsConstructor
public class VideoRoomWebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(VideoRoomWebSocketController.class);

    private final JanusVideoService janusVideoService;
    private final SimpMessagingTemplate messagingTemplate;

    private final ConcurrentHashMap<String, String> userSessionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userHandleMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userRoomMap = new ConcurrentHashMap<>();

    @MessageMapping("/video/register")
    public void register(Map<String, String> payload) {

        String userId = payload.get("userId");
        String sessionId = payload.get("sessionId");
        String handleId = payload.get("handleId");
        String roomId = payload.get("roomId");

        if (userId == null || sessionId == null || handleId == null || roomId == null) return;

        userSessionMap.put(userId, sessionId);
        userHandleMap.put(userId, handleId);
        userRoomMap.put(userId, roomId);

        janusVideoService.startEventPolling(sessionId, event -> {
            try {
                long sender = event.path("sender").asLong();
                if (sender == 0 || sender == Long.parseLong(handleId)) {
                    messagingTemplate.convertAndSend(
                            "/topic/video/" + roomId + "/answer/" + userId,
                            event
                    );
                }
            } catch (Exception e) {
                logger.error("Error forwarding Janus event", e);
            }
        });

        messagingTemplate.convertAndSend("/topic/video/" + roomId + "/events",
                Map.of("type", "joined", "userId", userId));
    }

    @MessageMapping("/video/unregister")
    public void unregister(Map<String, String> payload) {

        String userId = payload.get("userId");
        if (userId == null) return;

        String sessionId = userSessionMap.remove(userId);
        userHandleMap.remove(userId);
        String roomId = userRoomMap.remove(userId);

        if (sessionId != null)
            janusVideoService.stopEventPolling(sessionId);

        messagingTemplate.convertAndSend("/topic/video/" + roomId + "/events",
                Map.of("type", "left", "userId", userId));
    }

    @MessageMapping("/video/offer")
    public void offer(Map<String, String> payload) {

        String userId = payload.get("userId");
        String sdp = payload.get("sdp");
        String roomId = payload.get("roomId");

        if (userId == null || sdp == null || roomId == null) return;

        String sessionId = userSessionMap.get(userId);
        String handleId = userHandleMap.get(userId);

        janusVideoService.sendOffer(sessionId, handleId, sdp, userId, roomId, messagingTemplate);
    }

    @MessageMapping("/video/ice")
    public void ice(Map<String, Object> payload) {

        String userId = (String) payload.get("userId");
        Object candidate = payload.get("candidate");

        if (userId == null || candidate == null) return;

        String sessionId = userSessionMap.get(userId);
        String handleId = userHandleMap.get(userId);

        janusVideoService.sendIce(sessionId, handleId, candidate);
    }

    @MessageMapping("/video/mute")
    public void mute(Map<String, String> payload) {

        String userId = payload.get("userId");
        String action = payload.get("action");
        String roomId = payload.get("roomId");

        if (userId == null || roomId == null || action == null) return;

        boolean mute = action.equalsIgnoreCase("mute");

        String sessionId = userSessionMap.get(userId);
        String handleId = userHandleMap.get(userId);

        janusVideoService.setMute(sessionId, handleId, mute);

        messagingTemplate.convertAndSend(
                "/topic/video/" + roomId + "/events",
                Map.of("userId", userId, "type", mute ? "muted" : "unmuted")
        );
    }
}
