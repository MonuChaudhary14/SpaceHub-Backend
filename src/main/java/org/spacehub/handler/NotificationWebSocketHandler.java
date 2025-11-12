package org.spacehub.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler{

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
      System.out.println("Connected: " + email);
      System.out.println("Active WebSocket sessions: " + userSessions.keySet());
    }
    else {
      System.out.println("Connection rejected (no valid email param)");
      session.close(CloseStatus.BAD_DATA);
    }
  }

  @Override
  public void handleTextMessage(@NonNull WebSocketSession session, TextMessage message) {
    System.out.println("Message from client: " + message.getPayload());
  }

  @Override
  public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
    String email = getEmailFromSession(session);
    if (email != null) {
      email = email.toLowerCase();
      userSessions.remove(email);
      System.out.println("Disconnected: " + email);
    }
  }

  private String getEmailFromSession(WebSocketSession session) {
    String query = session.getUri() != null ? session.getUri().getQuery() : null;
    if (query == null) return null;

    for (String param : query.split("&")) {
      if (param.startsWith("email=")) {
        try {
          String encoded = param.split("=", 2)[1];
          return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
          return null;
        }
      }
    }
    return null;
  }

  public void sendNotification(String email, Object notificationData) {
    if (email == null) return;

    email = email.toLowerCase();

    WebSocketSession session = userSessions.get(email);

    System.out.println("Attempting to send notification to: " + email);
    System.out.println("Current active sessions: " + userSessions.keySet());

    if (session != null && session.isOpen()) {
      try {
        String json = objectMapper.writeValueAsString(notificationData);
        session.sendMessage(new TextMessage(json));
        System.out.println("Sent notification to " + email);
      }
      catch (IOException e) {
        System.err.println("Failed to send notification to " + email + ": " + e.getMessage());
      }
    }
    else {
      System.out.println("No active WebSocket session for: " + email);
    }
  }

}
