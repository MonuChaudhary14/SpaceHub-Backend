package org.spacehub.service.VideoRoom;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Getter
@Service
public class JanusVideoService {

  @Value("${janus.server.url:http://localhost:8088/janus}")
  private String janusUrl;

  private final RestTemplate restTemplate = new RestTemplate();
  private final ExecutorService pollExecutor = Executors.newCachedThreadPool();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final Map<String, Future<?>> pollingTasks = new ConcurrentHashMap<>();

  public void startEventPolling(String sessionId, java.util.function.Consumer<JsonNode> onEvent) {
    if (pollingTasks.containsKey(sessionId)) return;

    String sessionUrl = janusUrl + "/" + sessionId;

    Future<?> keepaliveFuture = scheduler.scheduleAtFixedRate(() -> {
      try {
        Map<String, String> request = Map.of("janus", "keepalive", "transaction", UUID.randomUUID().toString());
        restTemplate.postForEntity(sessionUrl, request, JsonNode.class);
      } catch (Exception e) {
        log.error("Failed keepalive for session {}: {}", sessionId, e.getMessage());
      }
    }, 25, 25, TimeUnit.SECONDS);

    Future<?> pollFuture = pollExecutor.submit(() -> {
      log.info("Started Janus Video poll for session={}", sessionId);
      while (!Thread.currentThread().isInterrupted()) {
        try {
          ResponseEntity<JsonNode> resp = restTemplate.getForEntity(sessionUrl, JsonNode.class);
          JsonNode body = resp.getBody();
          if (body != null && !body.isNull()) {
            if (body.isArray()) {
              for (JsonNode event : body) onEvent.accept(event);
            } else {
              onEvent.accept(body);
            }
          }
        } catch (Exception e) {
          if (Thread.currentThread().isInterrupted()) break;
          log.error("Error polling Janus Video for session {}: {}", sessionId, e.getMessage());
          try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
      }
      keepaliveFuture.cancel(true);
      log.info("Stopping Janus Video poll for session={}", sessionId);
    });

    pollingTasks.put(sessionId, pollFuture);
  }

  public void stopEventPolling(String sessionId) {
    Future<?> f = pollingTasks.remove(sessionId);
    if (f != null) f.cancel(true);
  }

  public String createSession() {
// ... rest of file (already updated in previous steps, but I'll make sure it's consistent)
    Map<String, Object> request = Map.of(
            "janus", "create", "transaction", UUID.randomUUID().toString()
    );

    ResponseEntity<JsonNode> response = restTemplate.postForEntity(janusUrl, request, JsonNode.class);

    if (response.getBody() != null && response.getBody().has("data")) {
      return response.getBody().get("data").get("id").asText();
    }
    throw new RuntimeException("Failed to create Janus session");
  }

  public String attachVideoRoomPlugin(String sessionId){

    Map<String, Object> request = Map.of(
            "janus", "attach", "plugin", "janus.plugin.videoroom",
            "transaction", UUID.randomUUID().toString()
    );

    String sessionUrl = janusUrl + "/" + sessionId;

    ResponseEntity<JsonNode> response = restTemplate.postForEntity(sessionUrl, request, JsonNode.class);

    if (response.getBody() != null && response.getBody().has("data")) {
      return response.getBody().get("data").get("id").asText();
    }
    throw new RuntimeException("Failed to attach VideoRoom plugin");

  }

  public void createVideoRoom(String sessionId, String handleId, int roomId, String description){

    Map<String, Object> body = Map.of(
             "request", "create", "room", roomId, "description", description, "bitrate",
      512000,
             "publishers", 10, "record", false, "is_private", false
    );

    Map<String, Object> request = Map.of(
            "janus", "message",
            "transaction", UUID.randomUUID().toString(),
            "body", body
    );

    String handleUrl = String.format("%s/%s/%s", janusUrl, sessionId, handleId);
    restTemplate.postForEntity(handleUrl, request, JsonNode.class);

  }

  public void joinVideoRoom(String sessionId, String handleId, int roomId, String displayName) {
    Map<String, Object> body = Map.of("request", "join", "ptype", "publisher", "room", roomId,
      "display", displayName);

    Map<String, Object> request = Map.of(
            "janus", "message", "transaction", UUID.randomUUID().toString(), "body", body);

    String handleUrl = String.format("%s/%s/%s", janusUrl, sessionId, handleId);
    restTemplate.postForEntity(handleUrl, request, JsonNode.class);
  }

  public JsonNode publishOwnFeed(String sessionId, String handleId, String offer) {
    Map<String, Object> body = Map.of("request", "publish", "audio", true, "video", true);

    Map<String, Object> request = Map.of(
            "janus", "message", "transaction", UUID.randomUUID().toString(), "body", body,
      "jsep", Map.of(
            "type", "offer", "sdp", offer)
    );

    String handleUrl = String.format("%s/%s/%s", janusUrl, sessionId, handleId);
    ResponseEntity<JsonNode> response = restTemplate.postForEntity(handleUrl, request, JsonNode.class);
    return response.getBody();
  }

  public JsonNode subscribeToFeed(String sessionId, String handleId, int roomId, int feedId) {
    Map<String, Object> body = Map.of(
            "request", "join", "room", roomId, "ptype", "subscriber", "feed", feedId);

    Map<String, Object> request = Map.of(
            "janus", "message", "transaction", UUID.randomUUID().toString(), "body", body);

    String handleUrl = String.format("%s/%s/%s", janusUrl, sessionId, handleId);
    ResponseEntity<JsonNode> response = restTemplate.postForEntity(handleUrl, request, JsonNode.class);
    return response.getBody();
  }

  public void leaveVideoRoom(String sessionId, String handleId) {
    Map<String, Object> body = Map.of(
            "request", "leave"
    );

    Map<String, Object> request = Map.of(
            "janus", "message", "transaction", UUID.randomUUID().toString(), "body", body);

    String handleUrl = String.format("%s/%s/%s", janusUrl, sessionId, handleId);
    restTemplate.postForEntity(handleUrl, request, JsonNode.class);
  }

}
