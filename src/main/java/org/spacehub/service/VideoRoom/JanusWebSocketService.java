package org.spacehub.service.VideoRoom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.WebSocketHttpHeaders;

import java.net.URI;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class JanusWebSocketService implements WebSocketHandler{

  @Value("${janus.ws-url:ws://localhost:8188/janus}")
  private String janusWsUrl;

  @Value("${janus.ws-enabled:true}")
  private boolean wsEnabled;

  private static final int INITIAL_RECONNECT_DELAY_SECONDS = 5;
  private static final int MAX_RECONNECT_DELAY_SECONDS = 60;
  private static final int MAX_RECONNECT_ATTEMPTS = 10;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
  private final WebSocketHttpHeaders headers = new WebSocketHttpHeaders();

  public JanusWebSocketService() {
    headers.setSecWebSocketProtocol(java.util.List.of("janus-protocol"));
  }

  private volatile WebSocketSession session;
  private volatile boolean manuallyClosed = false;
  private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

  @PostConstruct
  public void connect() {
    if (!wsEnabled) {
      log.info("Janus WebSocket is disabled (janus.ws-enabled=false). Skipping connection.");
      return;
    }
    manuallyClosed = false;
    reconnectAttempts.set(0);
    doConnect();
  }

  private void doConnect() {
    try {
      log.info("Connecting to Janus WebSocket at {}", janusWsUrl);

      webSocketClient
              .execute(this, headers, URI.create(janusWsUrl))
              .whenComplete((session, ex) -> {
                if (ex != null) {
                  log.error("WebSocket connection failed: {}", ex.getMessage());
                  scheduleReconnect();
                }
                else {
                  this.session = session;
                  reconnectAttempts.set(0);
                  log.info("Connected to Janus WebSocket (Session ID: {})", session.getId());
                }
              });

    }
    catch (Exception e) {
      log.error("Error during WebSocket connection: {}", e.getMessage(), e);
      scheduleReconnect();
    }
  }

  public synchronized void send(JsonNode message) {
    if (session == null || !session.isOpen()) {
      log.warn("WebSocket not connected. Message not sent.");
      return;
    }
    try {
      String json = objectMapper.writeValueAsString(message);
      session.sendMessage(new TextMessage(json));
      log.debug("Sent: {}", json);
    }
    catch (Exception e) {
      log.error("Failed to send message: {}", e.getMessage(), e);
    }
  }

  public synchronized void close() {
    manuallyClosed = true;
    try {
      if (session != null && session.isOpen()) {
        session.close();
        log.info("Closed WebSocket manually.");
      }
    }
    catch (Exception e) {
      log.error("Error closing WebSocket: {}", e.getMessage(), e);
    }
  }

  private void scheduleReconnect() {
    if (manuallyClosed) {
      return;
    }
    int attempt = reconnectAttempts.incrementAndGet();
    if (attempt > MAX_RECONNECT_ATTEMPTS) {
      log.warn("Max reconnect attempts ({}) reached for Janus WebSocket. Giving up. Call connect() to retry.", MAX_RECONNECT_ATTEMPTS);
      return;
    }
    int delay = Math.min(INITIAL_RECONNECT_DELAY_SECONDS * (1 << (attempt - 1)), MAX_RECONNECT_DELAY_SECONDS);
    log.info("Attempting reconnect {}/{} in {} seconds...", attempt, MAX_RECONNECT_ATTEMPTS, delay);
    scheduler.schedule(this::doConnect, delay, TimeUnit.SECONDS);
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    log.info("Connection established: {}", session.getId());
    this.session = session;
  }

  @Override
  public void handleMessage(@NonNull WebSocketSession session, @NonNull WebSocketMessage<?> message) {
    try {
      String payload = message.getPayload().toString();
      JsonNode json = objectMapper.readTree(payload);
      log.info("Received: {}", json);
    }
    catch (Exception e) {
      log.error("Failed to process message: {}", e.getMessage(), e);
    }
  }

  @Override
  public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
    log.error("Transport error: {}", exception.getMessage());
    scheduleReconnect();
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, @NonNull CloseStatus closeStatus) {
    log.warn("Connection closed [{}]: {}", session.getId(), closeStatus);
    this.session = null;
    if (!manuallyClosed) {
      scheduleReconnect();
    }
  }

  @Override
  public boolean supportsPartialMessages() {
    return false;
  }

}

