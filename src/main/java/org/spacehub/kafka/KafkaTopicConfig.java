package org.spacehub.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

  public static final String TOPIC_COMMUNITY_CHAT = "spacehub.chat.community";
  public static final String TOPIC_DIRECT_CHAT = "spacehub.chat.direct";
  public static final String TOPIC_OUTBOX_EVENTS = "spacehub.outbox.events";

  public static final int DEFAULT_PARTITIONS = 3;
  public static final short DEFAULT_REPLICATION_FACTOR = 1;

  @Bean
  public NewTopic communityChatTopic() {
    return TopicBuilder.name(TOPIC_COMMUNITY_CHAT)
      .partitions(DEFAULT_PARTITIONS)
      .replicas(DEFAULT_REPLICATION_FACTOR)
      .build();
  }

  @Bean
  public NewTopic directChatTopic() {
    return TopicBuilder.name(TOPIC_DIRECT_CHAT)
      .partitions(DEFAULT_PARTITIONS)
      .replicas(DEFAULT_REPLICATION_FACTOR)
      .build();
  }

  @Bean
  public NewTopic outboxEventsTopic() {
    return TopicBuilder.name(TOPIC_OUTBOX_EVENTS)
      .partitions(DEFAULT_PARTITIONS)
      .replicas(DEFAULT_REPLICATION_FACTOR)
      .build();
  }
}
