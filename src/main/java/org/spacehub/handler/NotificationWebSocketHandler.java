package org.spacehub.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.Notification.NotificationResponseDTO;
import org.spacehub.entities.Notification.Notification;
import org.spacehub.mapper.NotificationMapper;
import org.spacehub.repository.Notification.NotificationRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler{

  private final NotificationRepository notificationRepository;
  private final NotificationMapper notificationMapper;

  private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();

  private final ObjectMapper objectMapper = new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Override
  public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
    String email = getEmailFromSession(session);

    if (email != null) {
      email = email.toLowerCase();
      userSessions.put(email, session);

      System.out.println("WebSocket connected: " + email);
      sendPreviousNotifications(email);

      System.out.println("Active sessions: " + userSessions.keySet());
    }
    else {
      System.out.println("WebSocket rejected: email missing");
      session.close(CloseStatus.BAD_DATA);
    }
  }

  private void sendPreviousNotifications(String email) {
    try {
      List<Notification> history = notificationRepository.findAllByRecipientWithDetails(email);

      List<NotificationResponseDTO> dtoList = history.stream()
        .map(notificationMapper::mapToDTO)
        .toList();

      WebSocketSession session = userSessions.get(email);
      if (session != null && session.isOpen()) {
        for (NotificationResponseDTO dto : dtoList) {
          String json = objectMapper.writeValueAsString(dto);
          session.sendMessage(new TextMessage(json));
        }
      }

      System.out.println("Sent previous notifications to " + email);

    }
    catch (Exception ignored) {}
  }

//  @Override
//  public void handleTextMessage(@NonNull WebSocketSession session, TextMessage message) {
//    System.out.println("Message from client: " + message.getPayload());
//  }

  @Override
  public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
    String email = getEmailFromSession(session);
    if (email != null) {
      email = email.toLowerCase();
      userSessions.remove(email);
      System.out.println("WebSocket disconnected: " + email);
    }
  }

  private String getEmailFromSession(WebSocketSession session) {
    String query = session.getUri() != null ? session.getUri().getQuery() : null;
    if (query == null) return null;

    for (String param : query.split("&")) {
      if (param.startsWith("email=")) {
        try {
          return URLDecoder.decode(param.split("=", 2)[1], StandardCharsets.UTF_8);
        }
        catch (Exception ignored) {}
      }
    }
    return null;
  }

  public void sendNotification(String email, Object notificationData) {
    email = email.toLowerCase();
    WebSocketSession session = userSessions.get(email);

    if (session != null && session.isOpen()) {
      try {
        String json = objectMapper.writeValueAsString(notificationData);
        session.sendMessage(new TextMessage(json));
      }
      catch (IOException e) {
        System.err.println("Failed to send real-time notification: " + e.getMessage());
      }
    }
  }

}
