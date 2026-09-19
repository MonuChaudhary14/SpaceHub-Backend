package org.spacehub.service.WebSocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.DTO.WebSocket.WsRedisEnvelope;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class WsRedisPublisher {

  private static final Logger logger = LoggerFactory.getLogger(WsRedisPublisher.class);

  public static final String TOPIC_COMMUNITY_CHAT = "spacehub.ws.community-chat";
  public static final String TOPIC_DIRECT_CHAT = "spacehub.ws.direct-chat";
  public static final String TOPIC_NOTIFICATION = "spacehub.ws.notifications";
  public static final String TOPIC_PRESENCE = "spacehub.ws.presence";

  @Getter
  private final String nodeId = UUID.randomUUID().toString();

  private final RedisTemplate<String, Object> redisTemplate;
  private final ObjectMapper objectMapper;

  public WsRedisPublisher(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  public boolean publishCommunityChat(String roomCode, Object payload) {
    return publish(TOPIC_COMMUNITY_CHAT, "COMMUNITY_CHAT", roomCode, null, null, payload);
  }

  public boolean publishDirectChat(String chatKey, String senderEmail, String receiverEmail, Object payload) {
    return publish(TOPIC_DIRECT_CHAT, "DIRECT_CHAT", chatKey, senderEmail, receiverEmail, payload);
  }

  public boolean publishNotification(String recipientEmail, Object notificationData) {
    return publish(TOPIC_NOTIFICATION, "NOTIFICATION", recipientEmail, null, null, notificationData);
  }

  public boolean publishPresenceDelta(String email, String status, Long communityId, Object payload) {
    String targetId = communityId != null ? String.valueOf(communityId) : "global";
    return publish(TOPIC_PRESENCE, "PRESENCE_DELTA", targetId, email, null, payload);
  }

  private boolean publish(String topic, String eventType, String targetId,
                          String senderEmail, String receiverEmail, Object payload) {
    try {
      String payloadJson = payload instanceof String s ? s : objectMapper.writeValueAsString(payload);
      WsRedisEnvelope envelope = WsRedisEnvelope.builder()
        .topic(topic)
        .eventType(eventType)
        .targetId(targetId)
        .senderEmail(senderEmail)
        .receiverEmail(receiverEmail)
        .payloadJson(payloadJson)
        .originNodeId(nodeId)
        .timestamp(Instant.now().toEpochMilli())
        .build();

      String envelopeJson = objectMapper.writeValueAsString(envelope);
      redisTemplate.convertAndSend(topic, envelopeJson);
      return true;
    }
    catch (Exception e) {
      logger.warn("Failed to publish WebSocket event to Redis topic {}: {}", topic, e.getMessage());
      return false;
    }
  }

}
