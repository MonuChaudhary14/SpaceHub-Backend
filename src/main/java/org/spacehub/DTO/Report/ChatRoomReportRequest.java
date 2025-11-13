package org.spacehub.DTO.Report;

import lombok.Data;

@Data
public class ChatRoomReportRequest {

  private Long messageId;
  private String reporterEmail;
  private String senderEmail;
  private String chatRoomCode;
  private String communityCode;
  private String reason;

}
