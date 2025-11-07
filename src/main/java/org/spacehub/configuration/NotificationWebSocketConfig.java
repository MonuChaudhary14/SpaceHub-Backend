package org.spacehub.configuration;

import org.spacehub.handler.NotificationWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class NotificationWebSocketConfig implements WebSocketConfigurer{

    private final NotificationWebSocketHandler notificationWebSocketHandler;

    public NotificationWebSocketConfig(NotificationWebSocketHandler notificationWebSocketHandler) {
        this.notificationWebSocketHandler = notificationWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationWebSocketHandler, "/notification")
                .setAllowedOrigins(
                        "http://localhost:5173",
                        "https://codewithketan.me",
                        "https://space-hub-frontend.vercel.app",
                        "https://www.spacehubx.me",
                        "https://audio-room-tawny.vercel.app"
                );
    }

}
