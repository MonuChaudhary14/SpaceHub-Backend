package org.spacehub.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;

@Service
public class JanusService {

  private final String janusUrl = "http://localhost:8088/janus";

  private final RestTemplate restTemplate = new RestTemplate();

  public String createSession() {
    Map<String, String> request = Map.of(
            "janus", "create",
            "transaction", UUID.randomUUID().toString()
    );
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
            "request", "create",
            "room", roomId,
            "description", "SpaceHub Voice Room",
            "is_private", false
    );

    Map<String, Object> request = Map.of(
            "janus", "message",
            "transaction", UUID.randomUUID().toString(),
            "body", body
    );

    String handleUrl = String.format("%s/%s/%s", janusUrl, sessionId, handleId);
    restTemplate.postForEntity(handleUrl, request, JsonNode.class);
  }

  public JsonNode joinAudioRoom(String sessionId, String handleId, int roomId, String displayName) {
    Map<String, Object> body = Map.of(
            "request", "join",
            "room", roomId,
            "display", displayName
    );

    Map<String, Object> request = Map.of(
            "janus", "message",
            "transaction", UUID.randomUUID().toString(),
            "body", body
    );

    String handleUrl = String.format("%s/%s/%s", janusUrl, sessionId, handleId);
    restTemplate.postForEntity(handleUrl, request, JsonNode.class);

    return pollForPluginEvent(sessionId);
  }

  public JsonNode sendOffer(String sessionId, String handleId, String sdpOffer) {
    Map<String, Object> body = Map.of(
            "request", "configure",
            "muted", false,
            "audio", true
    );

    Map<String, Object> request = Map.of(
            "janus", "message",
            "transaction", UUID.randomUUID().toString(),
            "body", body,
            "jsep", Map.of(
                    "type", "offer",
                    "sdp", sdpOffer
            )
    );

    String handleUrl = String.format("%s/%s/%s", janusUrl, sessionId, handleId);
    restTemplate.postForEntity(handleUrl, request, JsonNode.class);

    JsonNode event = pollForPluginEvent(sessionId);

    if (event != null) {
      if (event.has("jsep") || (event.has("plugindata") &&
              event.get("plugindata").has("data") &&
              event.get("plugindata").get("data").has("jsep"))) {
        return event;
      }
    }

    throw new RuntimeException("Failed to receive valid SDP answer from Janus");
  }

  public void sendIce(String sessionId, String handleId, Object candidate) {
    Map<String, Object> request = Map.of(
            "janus", "trickle",
            "transaction", UUID.randomUUID().toString(),
            "candidate", candidate
    );
    String handleUrl = String.format("%s/%s/%s", janusUrl, sessionId, handleId);
    ResponseEntity<JsonNode> resp = restTemplate.postForEntity(handleUrl, request, JsonNode.class);
    System.out.println("Error for log -> sendIce -> " + resp.getStatusCode() + " body: " + resp.getBody());
  }

  public void setMute(String sessionId, String handleId, boolean mute) {
    Map<String, Object> body = Map.of(
            "request", "configure",
            "audio", !mute,
            "muted", mute
    );

    Map<String, Object> request = Map.of(
            "janus", "message",
            "transaction", UUID.randomUUID().toString(),
            "body", body
    );

    String handleUrl = String.format("%s/%s/%s", janusUrl, sessionId, handleId);
    restTemplate.postForEntity(handleUrl, request, JsonNode.class);
  }

  private JsonNode pollForPluginEvent(String sessionId) {
    try {
      for (int i = 0; i < 10; i++) {
        String sessionPollingUrl = String.format("%s/%s?rid=%d&maxev=1", janusUrl, sessionId,
                System.currentTimeMillis());
        ResponseEntity<JsonNode> resp = restTemplate.getForEntity(sessionPollingUrl, JsonNode.class);
        JsonNode body = resp.getBody();

        if (body == null) {
          Thread.sleep(400);
          continue;
        }

        JsonNode eventNode = null;

        if (body.isArray()) {
          for (JsonNode node : body) {
            eventNode = findJanusEvent(node);
            if (eventNode != null) {
              break;
            }
          }
        } else {
          eventNode = findJanusEvent(body);
        }

        if (eventNode != null) {
          return eventNode;
        }

        Thread.sleep((long) 400);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception ignored) {
    }
    return null;
  }

  public JsonNode fetchSessionEvents(String sessionId) {
    try {
      String url = String.format("%s/%s?rid=%d&maxev=1", janusUrl, sessionId, System.currentTimeMillis());
      ResponseEntity<JsonNode> resp = restTemplate.getForEntity(url, JsonNode.class);
      return resp.getBody();
    } catch (Exception ex) {
      return null;
    }
  }

  private JsonNode findJanusEvent(JsonNode node) {
    if (node.has("janus") && "event".equals(node.get("janus").asText())) {

      boolean hasJsep = node.has("jsep");
      boolean hasPluginData = node.has("plugindata") && node.get("plugindata").has("data");

      if (hasJsep || hasPluginData) {
        return node;
      }
    }
    return null;
  }
}
