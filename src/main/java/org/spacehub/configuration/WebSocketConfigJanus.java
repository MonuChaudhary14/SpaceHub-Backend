package org.spacehub.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfigJanus implements WebSocketMessageBrokerConfigurer {

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableSimpleBroker("/topic");
    config.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")
      .setAllowedOriginPatterns("*")
      .withSockJS();
//            .setAllowedOrigins("https://codewithketan.me", "http://127.0.0.1:5500", "http://localhost:5173",
//              "http://localhost:8080", "https://space-hub-frontend.vercel.app", "https://www.spacehubx.me",
//              "https://audio-room-tawny.vercel.app", "http://127.0.0.1:5500/coding/index.html",
//              "https://somiljain2006.github.io/Audio-room", "https://somiljain2006.github.io").withSockJS();
  }
}
