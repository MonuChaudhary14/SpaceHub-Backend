package org.spacehub.DTO.WebSocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WsRedisEnvelope implements Serializable {

  private String topic;
  private String eventType;
  private String targetId;
  private String senderEmail;
  private String receiverEmail;
  private String payloadJson;
  private String originNodeId;
  private long timestamp;

}
