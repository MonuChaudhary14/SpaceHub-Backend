package org.spacehub.configuration;

//import org.spacehub.service.LocationService;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.util.UriComponentsBuilder;
import java.security.Principal;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfigMessaging implements WebSocketMessageBrokerConfigurer {

//  private final LocationService locationService;
//
//  public WebSocketConfigMessaging(LocationService locationService) {
//    this.locationService = locationService;
//  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableSimpleBroker("/queue", "/topic");
    config.setApplicationDestinationPrefixes("/app");
    config.setUserDestinationPrefix("/user");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws-messages")
      .setAllowedOriginPatterns(
        "*",
        "https://codewithketan.me",
        "https://www.spacehubx.me",
        "https://space-hub-frontend.vercel.app",
        "http://localhost:5500",
        "http://127.0.0.1:5500",
        "http://localhost:8080"
      )
      .setHandshakeHandler(new DefaultHandshakeHandler() {
        @Override
        protected Principal determineUser(
          @NonNull ServerHttpRequest request,
          @NonNull WebSocketHandler wsHandler,
          @NonNull Map<String, Object> attributes
        ) {
          var params = UriComponentsBuilder.fromUri(request.getURI())
            .build()
            .getQueryParams();

          String username = params.getFirst("username");
//          String latStr = params.getFirst("lat");
//          String lonStr = params.getFirst("lon");

          if (username == null || username.isEmpty()) {
            return null;
          }

//          if (latStr != null && lonStr != null) {
//            try {
//              double lat = Double.parseDouble(latStr);
//              double lon = Double.parseDouble(lonStr);
//              locationService.updateLocation(username, lat, lon);
//            } catch (NumberFormatException ignored) {}
//          }

          return () -> username;
        }
      })
      .withSockJS();
  }
}
