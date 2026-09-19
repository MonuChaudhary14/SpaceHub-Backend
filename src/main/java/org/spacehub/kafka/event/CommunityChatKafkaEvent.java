package org.spacehub.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityChatKafkaEvent {
  private String messageUuid;
  private String senderEmail;
  private String message;
  private Long timestamp;
  private String fileName;
  private String fileUrl;
  private String contentType;
  private String roomCode;
  private String type;
}
