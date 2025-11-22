package org.spacehub.service.VideoRoom;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class JanusVideoService {

    @Value("${janus.server.url:http://localhost:8088/janus}")
    private String janusUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ExecutorService pollExecutor = Executors.newCachedThreadPool();
    private final ConcurrentMap<String, Future<?>> pollingTasks = new ConcurrentHashMap<>();

    public void startEventPolling(String sessionId, Consumer<JsonNode> onEvent) {

        if (pollingTasks.containsKey(sessionId)) return;

        String sessionUrl = janusUrl + "/" + sessionId;

        Future<?> future = pollExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    ResponseEntity<JsonNode> resp = restTemplate.getForEntity(sessionUrl, JsonNode.class);
                    JsonNode body = resp.getBody();

                    if (body != null) {
                        if (body.isArray()) {
                            for (JsonNode event : body) onEvent.accept(event);
                        }
                        else {
                            onEvent.accept(body);
                        }
                    }
                }
                catch (Exception ignored) {}
            }
        });

        pollingTasks.put(sessionId, future);
    }

    public void stopEventPolling(String sessionId) {
        Future<?> task = pollingTasks.remove(sessionId);
        if (task != null) task.cancel(true);
    }

    public String createSession() {
        Map<String, Object> req = Map.of("janus", "create", "transaction", UUID.randomUUID().toString());
        JsonNode data = restTemplate.postForEntity(janusUrl, req, JsonNode.class).getBody();
        return data.get("data").get("id").asText();
    }

    public String attachVideoRoomPlugin(String sessionId) {
        Map<String, Object> req = Map.of(
                "janus", "attach",
                "plugin", "janus.plugin.videoroom",
                "transaction", UUID.randomUUID().toString()
        );
        JsonNode data = restTemplate.postForEntity(janusUrl + "/" + sessionId, req, JsonNode.class).getBody();
        return data.get("data").get("id").asText();
    }

    public void createVideoRoom(String sessionId, String handleId, int roomId, String description) {

        Map<String, Object> body = Map.of(
                "request", "create",
                "room", roomId,
                "description", description,
                "publishers", 10,
                "bitrate", 512_000,
                "record", false,
                "is_private", false
        );

        sendMessage(sessionId, handleId, body);
    }

    public void joinVideoRoom(String sessionId, String handleId, int roomId, String displayName) {
        Map<String, Object> body = Map.of(
                "request", "join",
                "ptype", "publisher",
                "room", roomId,
                "display", displayName
        );
        sendMessage(sessionId, handleId, body);
    }

    public void sendOffer(String sessionId, String handleId, String sdp,
                          String userId, String roomId, SimpMessagingTemplate template) {

        Map<String, Object> body = Map.of(
                "request", "publish",
                "audio", true,
                "video", true);

        Map<String, Object> jsep = Map.of("type", "offer", "sdp", sdp);

        Map<String, Object> req = Map.of(
                "janus", "message",
                "transaction", UUID.randomUUID().toString(),
                "body", body,
                "jsep", jsep
        );

        String url = janusUrl + "/" + sessionId + "/" + handleId;

        JsonNode resp = restTemplate.postForEntity(url, req, JsonNode.class).getBody();

        template.convertAndSend("/topic/video/" + roomId + "/answer/" + userId, resp);
    }

    public void sendIce(String sessionId, String handleId, Object candidate) {

        Map<String, Object> req = Map.of(
                "janus", "trickle",
                "transaction", UUID.randomUUID().toString(),
                "candidate", candidate
        );

        restTemplate.postForEntity(janusUrl + "/" + sessionId + "/" + handleId, req, JsonNode.class);
    }

    public void setMute(String sessionId, String handleId, boolean mute) {

        Map<String, Object> body = Map.of(
                "request", "configure",
                "audio", !mute,
                "muted", mute
        );

        sendMessage(sessionId, handleId, body);
    }

    private void sendMessage(String sessionId, String handleId, Map<String, Object> body) {

        Map<String, Object> req = Map.of(
                "janus", "message",
                "transaction", UUID.randomUUID().toString(),
                "body", body
        );

        String url = janusUrl + "/" + sessionId + "/" + handleId;

        restTemplate.postForEntity(url, req, JsonNode.class);
    }
}
