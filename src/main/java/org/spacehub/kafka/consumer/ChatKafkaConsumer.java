package org.spacehub.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.NewChatRoom;
import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.handler.ChatWebSocketHandlerMessaging;
import org.spacehub.kafka.KafkaTopicConfig;
import org.spacehub.kafka.event.CommunityChatKafkaEvent;
import org.spacehub.kafka.event.DirectChatKafkaEvent;
import org.spacehub.repository.ChatRoom.NewChatRoomRepository;
import org.spacehub.service.Interface.IMessageService;
import org.spacehub.service.chatRoom.chatroomInterfaces.IChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChatKafkaConsumer {

  private static final Logger logger = LoggerFactory.getLogger(ChatKafkaConsumer.class);

  private final IChatMessageService chatMessageService;
  private final IMessageService directMessageService;
  private final NewChatRoomRepository newChatRoomRepository;
  private final ObjectMapper objectMapper;

  private ChatWebSocketHandlerMessaging messagingHandler;

  @Autowired
  @Lazy
  public void setMessagingHandler(ChatWebSocketHandlerMessaging handler) {
    this.messagingHandler = handler;
  }

  @KafkaListener(
    topics = KafkaTopicConfig.TOPIC_COMMUNITY_CHAT,
    containerFactory = "kafkaBatchListenerContainerFactory",
    groupId = "spacehub-community-chat-ingestion-group"
  )
  public void consumeCommunityChatBatch(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
    if (records == null || records.isEmpty()) {
      ack.acknowledge();
      return;
    }

    logger.debug("Received community chat batch of size: {}", records.size());
    List<ChatMessage> messagesToSave = new ArrayList<>(records.size());

    for (ConsumerRecord<String, String> record : records) {
      ChatMessage msg = parseCommunityRecord(record.value());
      if (msg != null) {
        messagesToSave.add(msg);
      }
    }

    try {
      if (!messagesToSave.isEmpty()) {
        chatMessageService.saveAll(messagesToSave);
        logger.info("Successfully persisted Kafka batch of {} community messages", messagesToSave.size());
      }
      ack.acknowledge();
    } catch (Exception e) {
      logger.error("Failed to persist community chat batch into PostgreSQL: {}", e.getMessage(), e);
      throw e;
    }
  }

  private ChatMessage parseCommunityRecord(String json) {
    try {
      CommunityChatKafkaEvent event = objectMapper.readValue(json, CommunityChatKafkaEvent.class);
      ChatMessage message = ChatMessage.builder()
        .messageUuid(event.getMessageUuid())
        .senderEmail(event.getSenderEmail())
        .message(event.getMessage())
        .timestamp(event.getTimestamp() != null ? event.getTimestamp() : System.currentTimeMillis())
        .fileName(event.getFileName())
        .fileUrl(event.getFileUrl())
        .contentType(event.getContentType())
        .roomCode(event.getRoomCode())
        .type(event.getType() != null ? event.getType() : "MESSAGE")
        .build();

      if (event.getRoomCode() != null) {
        try {
          Optional<NewChatRoom> roomOpt = newChatRoomRepository.findByRoomCode(UUID.fromString(event.getRoomCode()));
          roomOpt.ifPresent(message::setNewChatRoom);
        } catch (Exception ignored) {
        }
      }
      return message;
    } catch (Exception e) {
      logger.error("Error deserializing community chat event: {}", json, e);
      return null;
    }
  }

  @KafkaListener(
    topics = KafkaTopicConfig.TOPIC_DIRECT_CHAT,
    containerFactory = "kafkaBatchListenerContainerFactory",
    groupId = "spacehub-direct-chat-ingestion-group"
  )
  public void consumeDirectChatBatch(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
    if (records == null || records.isEmpty()) {
      ack.acknowledge();
      return;
    }

    logger.debug("Received direct chat batch of size: {}", records.size());
    List<Message> messagesToSave = new ArrayList<>(records.size());

    for (ConsumerRecord<String, String> record : records) {
      Message msg = parseDirectRecord(record.value());
      if (msg != null) {
        messagesToSave.add(msg);
      }
    }

    try {
      if (!messagesToSave.isEmpty()) {
        List<Message> persisted = directMessageService.saveMessageBatch(messagesToSave);
        logger.info("Successfully persisted Kafka batch of {} direct messages", messagesToSave.size());
        notifyPersistedDirectMessages(persisted);
      }
      ack.acknowledge();
    } catch (Exception e) {
      logger.error("Failed to persist direct chat batch into PostgreSQL: {}", e.getMessage(), e);
      throw e;
    }
  }

  private Message parseDirectRecord(String json) {
    try {
      DirectChatKafkaEvent event = objectMapper.readValue(json, DirectChatKafkaEvent.class);
      return Message.builder()
        .messageUuid(event.getMessageUuid())
        .senderEmail(event.getSenderEmail())
        .receiverEmail(event.getReceiverEmail())
        .content(event.getContent())
        .fileKey(event.getFileKey())
        .fileName(event.getFileName())
        .contentType(event.getContentType())
        .timestamp(event.getTimestamp() != null ? event.getTimestamp() : System.currentTimeMillis())
        .type(event.getType() != null ? event.getType() : "MESSAGE")
        .readStatus(event.getReadStatus() != null ? event.getReadStatus() : Boolean.FALSE)
        .build();
    } catch (Exception e) {
      logger.error("Error deserializing direct chat event: {}", json, e);
      return null;
    }
  }

  private void notifyPersistedDirectMessages(List<Message> persisted) {
    if (messagingHandler == null || persisted == null) {
      return;
    }
    for (Message msg : persisted) {
      try {
        messagingHandler.confirmAndBroadcast(msg);
      } catch (Exception ignored) {
      }
    }
  }
}
