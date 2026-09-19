package org.spacehub.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectChatKafkaEvent {
  private String messageUuid;
  private String senderEmail;
  private String receiverEmail;
  private String content;
  private String fileKey;
  private String fileName;
  private String contentType;
  private Long timestamp;
  private String type;
  private Boolean readStatus;
}
