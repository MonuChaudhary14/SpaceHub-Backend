package org.spacehub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.WebSocketHttpHeaders;

import java.net.URI;
import java.util.concurrent.*;

@Slf4j
@Service
public class JanusWebSocketService implements WebSocketHandler{

    private static final String JANUS_WS_URL = "ws://host.docker.internal:8188";
    private static final int RECONNECT_DELAY_SECONDS = 5;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
    private final WebSocketHttpHeaders headers = new WebSocketHttpHeaders();

    private volatile WebSocketSession session;
    private volatile boolean manuallyClosed = false;

    @PostConstruct
    public void connect() {
        manuallyClosed = false;
        try {
            log.info("Connecting to Janus WebSocket at {}", JANUS_WS_URL);

            CompletableFuture<WebSocketSession> connectionFuture = new CompletableFuture<>();

            webSocketClient
                    .execute(this, headers, URI.create(JANUS_WS_URL))
                    .whenComplete((session, ex) -> {
                        if (ex != null) {
                            log.error("WebSocket connection failed: {}", ex.getMessage());
                            connectionFuture.completeExceptionally(ex);
                            scheduleReconnect();
                        }
                        else {
                            this.session = session;
                            connectionFuture.complete(session);
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
        if (!manuallyClosed) {
            log.info("Attempting reconnect in {} seconds...", RECONNECT_DELAY_SECONDS);
            scheduler.schedule(this::connect, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Connection established: {}", session.getId());
        this.session = session;
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
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
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Transport error: {}", exception.getMessage());
        scheduleReconnect();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
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
