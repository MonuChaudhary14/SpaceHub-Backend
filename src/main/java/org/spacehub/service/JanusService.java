package org.spacehub.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Service
public class JanusService {

  private static final Logger logger = LoggerFactory.getLogger(JanusService.class);

  @Value("${janus.server.url:http://localhost:8088/janus}")
  private String janusUrl;

  private final RestTemplate restTemplate = new RestTemplate();
  private final ExecutorService pollExecutor = Executors.newCachedThreadPool();
  private final ConcurrentMap<String, Future<?>> pollingTasks = new ConcurrentHashMap<>();

  public void startEventPolling(String sessionId, Consumer<JsonNode> onEvent) {
    if (pollingTasks.containsKey(sessionId)) {
      logger.warn("Polling for session {} already active.", sessionId);
      return;
    }

    String sessionUrl = String.format("%s/%s", janusUrl, sessionId);

    Future<?> future = pollExecutor.submit(() -> {
      logger.info("Started Janus poll for session={}", sessionId);
      while (!Thread.currentThread().isInterrupted()) {
        try {
          ResponseEntity<JsonNode> resp = restTemplate.getForEntity(sessionUrl, JsonNode.class);
          JsonNode body = resp.getBody();

          if (body != null && !body.isNull()) {
            if (body.isArray()) {
              for (JsonNode event : body) {
                onEvent.accept(event);
              }
            } else {
              onEvent.accept(body);
            }
          }
        } catch (Exception e) {
          if (Thread.currentThread().isInterrupted()) {
            break;
          }
          logger.error("Error polling Janus for session {}: {}", sessionId, e.getMessage());
          try {
            Thread.sleep(500);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
          }
        }
      }
      logger.info("Stopping Janus poll for session={}", sessionId);
    });

    pollingTasks.put(sessionId, future);
  }

  public void stopEventPolling(String sessionId) {
    Future<?> f = pollingTasks.remove(sessionId);
    if (f != null) {
      f.cancel(true);
    }
  }

  public String createSession() {
    Map<String, String> request = Map.of("janus", "create", "transaction", UUID.randomUUID().toString());
    ResponseEntity<JsonNode> response = restTemplate.postForEntity(janusUrl, request, JsonNode.class);
    if (response.getBody() != null && response.getBody().has("data")) {
      return response.getBody().get("data").get("id").asText();
    }
    throw new RuntimeException("Failed to create Janus session");
  }

  public String attachAudioBridgePlugin(String sessionId) {
    Map<String, String> request = Map.of(
      "janus", "attach",
      "plugin", "janus.plugin.audiobridge",
      "transaction", UUID.randomUUID().toString()
    );
    String sessionUrl = janusUrl + "/" + sessionId;
    ResponseEntity<JsonNode> response = restTemplate.postForEntity(sessionUrl, request, JsonNode.class);
    if (response.getBody() != null && response.getBody().has("data")) {
      return response.getBody().get("data").get("id").asText();
    }
    throw new RuntimeException("Failed to attach AudioBridge plugin");
  }

  public void createAudioRoom(String sessionId, String handleId, int roomId) {
    Map<String, Object> body = Map.of(
      "request", "create", "room", roomId, "description", "SpaceHub Voice Room",
      "is_private", false
    );
    Map<String, Object> request = Map.of(
      "janus", "message", "transaction", UUID.randomUUID().toString(), "body", body
    );
    String handleUrl = String.format("%s/%s/%s", janusUrl, sessionId, handleId);
    restTemplate.postForEntity(handleUrl, request, JsonNode.class);
  }

  public void joinAudioRoom(String sessionId, String handleId, int roomId, String displayName) {
    Map<String, Object> body = Map.of(
      "request", "join", "room", roomId, "display", displayName
    );
    Map<String, Object> request = Map.of(
      "janus", "message", "transaction", UUID.randomUUID().toString(), "body", body
    );
    String handleUrl = String.format("%s/%s/%s", janusUrl, sessionId, handleId);
    restTemplate.postForEntity(handleUrl, request, JsonNode.class);
  }

  public void sendOffer(String sessionId, String handleId, String sdpOffer) {
    Map<String, Object> body = Map.of("request", "configure", "muted", false, "audio", true);
    Map<String, Object> jsep = Map.of("type", "offer", "sdp", sdpOffer);
    Map<String, Object> request = Map.of(
      "janus", "message", "transaction", UUID.randomUUID().toString(), "body", body, "jsep",
      jsep
    );
    String handleUrl = String.format("%s/%s/%s", janusUrl, sessionId, handleId);
    restTemplate.postForEntity(handleUrl, request, JsonNode.class);
  }

  public void sendIce(String sessionId, String handleId, Object candidate) {
    Map<String, Object> request = Map.of(
      "janus", "trickle", "transaction", UUID.randomUUID().toString(), "candidate", candidate
    );
    String handleUrl = String.format("%s/%s/%s", janusUrl, sessionId, handleId);
    restTemplate.postForEntity(handleUrl, request, JsonNode.class);
  }

  public void setMute(String sessionId, String handleId, boolean mute) {
    Map<String, Object> body = Map.of("request", "configure", "audio", !mute, "muted", mute);
    Map<String, Object> request = Map.of(
      "janus", "message", "transaction", UUID.randomUUID().toString(), "body", body
    );
    String handleUrl = String.format("%s/%s/%s", janusUrl, sessionId, handleId);
    restTemplate.postForEntity(handleUrl, request, JsonNode.class);
  }
}
