package org.spacehub.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class JanusVideoService {

    @Value("${janus.base-url}")
    private String janusUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public String createSession() {
        Map<String , Object> request = Map.of(
                "janus", "create", "transaction", UUID.randomUUID().toString()
        );

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(janusUrl , request, JsonNode.class);

        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data").get("id").asText();
        }
        throw new RuntimeException("Failed to create Janus session");
    }

    public String attachVideoRoomPlugin(String sessionId){

        Map<String , Object> request = Map.of(
                "janus", "attach", "plugin" , "janus.plugin.videoroom",
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
                 "request", "create", "room", roomId,"description", description,"bitrate", 512000,
                 "publishers", 10,"record", false,"is_private", false
        );

        Map<String, Object> request = Map.of(
                "janus", "message",
                "transaction", UUID.randomUUID().toString(),
                "body", body
        );

        String handleUrl = String.format("%s/%s/%s", janusUrl, sessionId, handleId);
        restTemplate.postForEntity(handleUrl, request, JsonNode.class);

    }

}
