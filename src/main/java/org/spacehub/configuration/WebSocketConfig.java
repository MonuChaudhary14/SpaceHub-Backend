package org.spacehub.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
import org.spacehub.handler.ChatWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer{

  private final ChatWebSocketHandler chatWebSocketHandler;

  public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler) {
    this.chatWebSocketHandler = chatWebSocketHandler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(chatWebSocketHandler, "/chat")
            .setAllowedOrigins(
                    "https://codewithketan.me",
                    "https://space-hub-frontend.vercel.app",
                    "https://www.spacehubx.me",
                    "https://audio-room-tawny.vercel.app"
            );
  }
}
