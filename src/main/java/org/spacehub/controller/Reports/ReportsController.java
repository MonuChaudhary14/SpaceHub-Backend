package org.spacehub.controller.Reports;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.Report.DirectMessageReportRequest;
import org.spacehub.DTO.Report.ChatRoomReportRequest;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.Interface.IReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/report")
public class ReportsController {

  private final IReportService reportService;

  @PostMapping("/direct")
  public ResponseEntity<ApiResponse<Map<String, Object>>> reportDirect(@RequestBody DirectMessageReportRequest request) {
    return ResponseEntity.ok(reportService.reportDirectMessage(request));
  }

  @PostMapping("/chatroom")
  public ResponseEntity<ApiResponse<Map<String, Object>>> reportChatRoom(@RequestBody ChatRoomReportRequest request) {
    return ResponseEntity.ok(reportService.reportChatRoomMessage(request));
  }

}
