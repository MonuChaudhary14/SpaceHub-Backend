package org.spacehub.service.WebSocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.DTO.WebSocket.WsRedisEnvelope;
import org.spacehub.handler.ChatWebSocketHandler;
import org.spacehub.handler.ChatWebSocketHandlerMessaging;
import org.spacehub.handler.NotificationWebSocketHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class WsRedisMessageSubscriber implements MessageListener {

  private static final Logger logger = LoggerFactory.getLogger(WsRedisMessageSubscriber.class);

  private final ChatWebSocketHandler chatWebSocketHandler;
  private final ChatWebSocketHandlerMessaging chatWebSocketHandlerMessaging;
  private final NotificationWebSocketHandler notificationWebSocketHandler;
  private final ObjectMapper objectMapper;

  public WsRedisMessageSubscriber(
    @Lazy ChatWebSocketHandler chatWebSocketHandler,
    @Lazy ChatWebSocketHandlerMessaging chatWebSocketHandlerMessaging,
    @Lazy NotificationWebSocketHandler notificationWebSocketHandler,
    ObjectMapper objectMapper) {
    this.chatWebSocketHandler = chatWebSocketHandler;
    this.chatWebSocketHandlerMessaging = chatWebSocketHandlerMessaging;
    this.notificationWebSocketHandler = notificationWebSocketHandler;
    this.objectMapper = objectMapper;
  }

  @Override
  public void onMessage(@NonNull Message message, byte[] pattern) {
    try {
      String rawBody = new String(message.getBody(), StandardCharsets.UTF_8);
      if (rawBody.startsWith("\"") && rawBody.endsWith("\"")) {
        rawBody = objectMapper.readValue(rawBody, String.class);
      }
      WsRedisEnvelope envelope = objectMapper.readValue(rawBody, WsRedisEnvelope.class);
      if (envelope != null && envelope.getEventType() != null) {
        dispatchEnvelope(envelope);
      }
    } catch (Exception e) {
      logger.error("Error processing Redis WebSocket message: {}", e.getMessage(), e);
    }
  }

  private void dispatchEnvelope(WsRedisEnvelope envelope) {
    switch (envelope.getEventType()) {
      case "COMMUNITY_CHAT" -> handleCommunityChat(envelope);
      case "DIRECT_CHAT" -> handleDirectChat(envelope);
      case "NOTIFICATION" -> handleNotification(envelope);
      case "PRESENCE_DELTA" -> handlePresenceDelta(envelope);
      default -> logger.debug("Unhandled WsRedis event type: {}", envelope.getEventType());
    }
  }

  private void handleCommunityChat(WsRedisEnvelope envelope) {
    if (envelope.getTargetId() != null && envelope.getPayloadJson() != null) {
      chatWebSocketHandler.broadcastToLocalRoom(envelope.getTargetId(), envelope.getPayloadJson());
    }
  }

  private void handleDirectChat(WsRedisEnvelope envelope) {
    if (envelope.getPayloadJson() != null) {
      chatWebSocketHandlerMessaging.broadcastToLocal(
        envelope.getTargetId(),
        envelope.getSenderEmail(),
        envelope.getReceiverEmail(),
        envelope.getPayloadJson()
      );
    }
  }

  private void handleNotification(WsRedisEnvelope envelope) {
    if (envelope.getTargetId() != null && envelope.getPayloadJson() != null) {
      notificationWebSocketHandler.sendNotificationToLocalSession(
        envelope.getTargetId(),
        envelope.getPayloadJson()
      );
    }
  }

  private void handlePresenceDelta(WsRedisEnvelope envelope) {
    if (envelope.getPayloadJson() != null && envelope.getSenderEmail() != null) {
      logger.debug("Received presence delta for user {}: {}", envelope.getSenderEmail(), envelope.getPayloadJson());
    }
  }

}
