package org.spacehub.DTO.Report;

import lombok.Data;

@Data
public class DirectMessageReportRequest {
    private Long messageId;
    private String reporterEmail;
    private String senderEmail;
    private String receiverEmail;
    private String reason;
}
