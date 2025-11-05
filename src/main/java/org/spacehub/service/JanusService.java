package org.spacehub.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;

@Service
public class JanusService {

    private final String janusUrl = "http://172.17.0.2:8088/janus";

    private final RestTemplate restTemplate = new RestTemplate();

    public String createSession() {
        Map<String, Object> request = Map.of(
                "janus", "create",
                "transaction", UUID.randomUUID().toString()
        );

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(janusUrl, request, JsonNode.class);
        if (response.getBody() == null || !response.getBody().has("data")) {
            throw new RuntimeException("Failed to create Janus session: " + response);
        }

        String sessionId = response.getBody().get("data").get("id").asText();
        System.out.println("Created session: " + sessionId);
        return sessionId;
    }

    public String attachAudioBridgePlugin(String sessionId) {
        Map<String, Object> request = Map.of(
                "janus", "attach",
                "plugin", "janus.plugin.audiobridge",
                "transaction", UUID.randomUUID().toString()
        );

        String url = String.format("%s/%s", janusUrl, sessionId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        if (response.getBody() == null || !response.getBody().has("data")) {
            throw new RuntimeException("Failed to attach plugin: " + response);
        }

        String handleId = response.getBody().get("data").get("id").asText();
        System.out.println("Attached AudioBridge plugin: " + handleId);
        return handleId;
    }

    public JsonNode createAudioRoom(String sessionId, String handleId, int roomId) {
        Map<String, Object> body = Map.of(
                "request", "create",
                "room", roomId,
                "description", "SpaceHub Voice Room " + roomId,
                "is_private", false,
                "sampling_rate", 48000
        );

        Map<String, Object> request = Map.of(
                "janus", "message",
                "transaction", UUID.randomUUID().toString(),
                "body", body
        );

        String handleUrl = String.format("%s/%s/%s", janusUrl, sessionId, handleId);
        ResponseEntity<JsonNode> resp = restTemplate.postForEntity(handleUrl, request, JsonNode.class);

        System.out.println("[JanusService] createAudioRoom response: " + resp.getStatusCode() + " | " + resp.getBody());

        JsonNode event = pollForPluginEvent(sessionId);
        if (event == null) {
            throw new RuntimeException("Timed out waiting for audiobridge 'created' event for room: " + roomId);
        }

        if (event.has("plugindata") && event.get("plugindata").has("data")) {
            JsonNode data = event.get("plugindata").get("data");
            if (data.has("audiobridge")) {
                String status = data.get("audiobridge").asText();
                if ("created".equalsIgnoreCase(status) || "event".equalsIgnoreCase(status)) {
                    System.out.println("Room confirmed: " + roomId + " (" + status + ")");
                    return event;
                }
            }
        }

        System.out.println("Unexpected event after create: " + event);
        return event;
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
        ResponseEntity<JsonNode> resp = restTemplate.postForEntity(handleUrl, request, JsonNode.class);
        System.out.println("joinAudioRoom response: " + resp.getStatusCode() + " | " + resp.getBody());

        JsonNode event = pollForPluginEvent(sessionId);
        return event;
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
            for (int i = 0; i < 25; i++) {
                String pollUrl = String.format("%s/%s?rid=%d&maxev=1", janusUrl, sessionId, System.currentTimeMillis());
                ResponseEntity<JsonNode> resp = restTemplate.getForEntity(pollUrl, JsonNode.class);
                JsonNode body = resp.getBody();

                if (body == null) {
                    Thread.sleep(300);
                    continue;
                }

                JsonNode eventNode = null;
                if (body.isArray()) {
                    for (JsonNode node : body) {
                        eventNode = findJanusEvent(node);
                        if (eventNode != null) break;
                    }
                } else {
                    eventNode = findJanusEvent(body);
                }

                if (eventNode != null) {
                    System.out.println("Plugin event: " + eventNode);
                    return eventNode;
                }

                Thread.sleep(300);
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            if (node.has("plugindata") && node.get("plugindata").has("plugin")) {
                String plugin = node.get("plugindata").get("plugin").asText();
                if ("janus.plugin.audiobridge".equals(plugin)) {
                    return node;
                }
            }
        }
        return null;
    }
}