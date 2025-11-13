package org.spacehub.service.Interface;

import org.spacehub.DTO.Report.DirectMessageReportRequest;
import org.spacehub.DTO.Report.ChatRoomReportRequest;
import org.spacehub.entities.ApiResponse.ApiResponse;

import java.util.Map;

public interface IReportService {

    ApiResponse<Map<String, Object>> reportDirectMessage(DirectMessageReportRequest request);

    ApiResponse<Map<String, Object>> reportChatRoomMessage(ChatRoomReportRequest request);

}
