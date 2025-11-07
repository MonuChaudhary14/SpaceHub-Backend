package org.spacehub.configuration;

import org.spacehub.handler.ChatWebSocketHandlerMessaging;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfigMessaging implements WebSocketConfigurer {

  private final ChatWebSocketHandlerMessaging chatWebSocketHandler;

  public WebSocketConfigMessaging(ChatWebSocketHandlerMessaging chatWebSocketHandler) {
    this.chatWebSocketHandler = chatWebSocketHandler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(chatWebSocketHandler, "/ws/direct-chat")
            .setAllowedOriginPatterns(
                    "*",
                    "https://codewithketan.me",
                    "https://www.spacehubx.me",
                    "https://space-hub-frontend.vercel.app",
                    "http://localhost:5500",
                    "http://127.0.0.1:5500",
                    "http://localhost:8080"
      );
  }
}
