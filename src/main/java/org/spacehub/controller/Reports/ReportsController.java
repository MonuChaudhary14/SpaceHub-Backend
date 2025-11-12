package org.spacehub.controller.Reports;

import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.Reports.ChatRoomMessageReport;
import org.spacehub.entities.Reports.DirectMessageReport;
import org.spacehub.entities.Reports.ReportStatus;
import org.spacehub.repository.Reports.ChatRoomMessageReportRepository;
import org.spacehub.repository.Reports.DirectMessageReportRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/report")
public class ReportsController {

    private final DirectMessageReportRepository directReport;
    private final ChatRoomMessageReportRepository chatReport;

    public ReportsController(DirectMessageReportRepository directReport,
                            ChatRoomMessageReportRepository chatReport) {
        this.directReport = directReport;
        this.chatReport = chatReport;
    }

    @PostMapping("/direct")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reportDirectMessage(@RequestBody Map<String, Object> req) {

        DirectMessageReport report = DirectMessageReport.builder()
                .messageId(Long.parseLong(req.get("messageId").toString()))
                .reporterEmail(req.get("reporterEmail").toString())
                .senderEmail(req.get("senderEmail").toString())
                .receiverEmail(req.get("receiverEmail").toString())
                .reason(req.getOrDefault("reason", "No reason provided").toString())
                .status(ReportStatus.PENDING)
                .reportedAt(LocalDateTime.now())
                .build();

        DirectMessageReport saved = directReport.save(report);

        return ResponseEntity.ok(new ApiResponse<>(200, "Direct message reported successfully",
                Map.of("reportId", saved.getId(), "messageId", saved.getMessageId(), "status", saved.getStatus().toString())));
    }

    @PostMapping("/chatroom")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reportChatRoomMessage(@RequestBody Map<String, Object> req) {

        ChatRoomMessageReport report = ChatRoomMessageReport.builder()
                .messageId(Long.parseLong(req.get("messageId").toString()))
                .reporterEmail(req.get("reporterEmail").toString())
                .senderEmail(req.get("senderEmail").toString())
                .chatRoomCode(req.get("chatRoomCode").toString())
                .communityCode((String) req.getOrDefault("communityCode", null))
                .reason(req.getOrDefault("reason", "No reason provided").toString())
                .status(ReportStatus.PENDING)
                .reportedAt(LocalDateTime.now())
                .build();

        ChatRoomMessageReport saved = chatReport.save(report);

        return ResponseEntity.ok(new ApiResponse<>(200, "ChatRoom message reported successfully",
                Map.of("reportId", saved.getId(), "messageId", saved.getMessageId(), "status", saved.getStatus().toString())));
    }



}
