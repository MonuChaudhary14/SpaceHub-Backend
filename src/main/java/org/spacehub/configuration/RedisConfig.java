package org.spacehub.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import java.time.Duration;

@Configuration
public class RedisConfig {

  @Value("${REDIS_HOST}")
  private String redisHost;

  @Value("${REDIS_PORT}")
  private int redisPort;

  @Bean
  public LettuceConnectionFactory redisConnectionFactory() {
    RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(redisHost, redisPort);

    LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
      .commandTimeout(Duration.ofSeconds(60))
      .shutdownTimeout(Duration.ofSeconds(10))
      .build();

    return new LettuceConnectionFactory(redisConfig, clientConfig);
  }

  @Bean
  public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new StringRedisSerializer());
    return template;
  }

  @Bean
  public org.springframework.data.redis.listener.RedisMessageListenerContainer redisMessageListenerContainer(
    LettuceConnectionFactory connectionFactory,
    org.spacehub.service.WebSocket.WsRedisMessageSubscriber subscriber) {
    org.springframework.data.redis.listener.RedisMessageListenerContainer container =
      new org.springframework.data.redis.listener.RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(subscriber,
      new org.springframework.data.redis.listener.ChannelTopic(org.spacehub.service.WebSocket.WsRedisPublisher.TOPIC_COMMUNITY_CHAT));
    container.addMessageListener(subscriber,
      new org.springframework.data.redis.listener.ChannelTopic(org.spacehub.service.WebSocket.WsRedisPublisher.TOPIC_DIRECT_CHAT));
    container.addMessageListener(subscriber,
      new org.springframework.data.redis.listener.ChannelTopic(org.spacehub.service.WebSocket.WsRedisPublisher.TOPIC_NOTIFICATION));
    return container;
  }

}
