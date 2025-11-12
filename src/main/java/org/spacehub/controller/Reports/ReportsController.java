package org.spacehub.controller.Reports;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.Notification.NotificationRequestDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.Community.CommunityUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.entities.Reports.ChatRoomMessageReport;
import org.spacehub.entities.Reports.DirectMessageReport;
import org.spacehub.entities.Reports.ReportStatus;
import org.spacehub.entities.User.User;
import org.spacehub.entities.Notification.NotificationType;
import org.spacehub.repository.Reports.ChatRoomMessageReportRepository;
import org.spacehub.repository.Reports.DirectMessageReportRepository;
import org.spacehub.repository.User.UserRepository;
import org.spacehub.repository.community.CommunityRepository;
import org.spacehub.service.Notification.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/report")
public class ReportsController {

    private final DirectMessageReportRepository directReport;
    private final ChatRoomMessageReportRepository chatReport;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;

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

        if (report.getCommunityCode() != null) {
            try {
                UUID communityId = UUID.fromString(report.getCommunityCode());
                communityRepository.findByCommunityCode(communityId).ifPresent(community -> {
                    notifyOwnerAndAdmins(report, community, saved.getId());
                });
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid communityCode UUID: " + report.getCommunityCode());
            }
        }

        return ResponseEntity.ok(new ApiResponse<>(200, "ChatRoom message reported successfully",
                Map.of("reportId", saved.getId(), "messageId", saved.getMessageId(), "status", saved.getStatus().toString())));
    }

    private void notifyOwnerAndAdmins(ChatRoomMessageReport report, Community community, UUID reportId) {
        String reporter = report.getReporterEmail();
        String chatRoomCode = report.getChatRoomCode();

        Set<User> owners = community.getCommunityUsers().stream()
                .filter(cu -> cu.getRole() == Role.WORKSPACE_OWNER && !cu.isBlocked() && !cu.isBanned())
                .map(CommunityUser::getUser).collect(Collectors.toSet());

        if (owners.isEmpty() && community.getCreatedBy() != null) {
            owners.add(community.getCreatedBy());
        }

        for (User owner : owners) {
            if (owner != null && owner.getEmail() != null) {
                NotificationRequestDTO dto = NotificationRequestDTO.builder()
                        .email(owner.getEmail())
                        .senderEmail(reporter)
                        .type(NotificationType.SYSTEM_UPDATE)
                        .title("Message reported in your workspace")
                        .message("A message in chat room `" + chatRoomCode + "` was reported by " + reporter + ". Please review it.")
                        .scope("community")
                        .referenceId(reportId)
                        .actionable(true)
                        .communityId(community.getId())
                        .build();
                notificationService.createNotification(dto);
            }
        }

        Set<User> admins = community.getCommunityUsers().stream()
                .filter(cu -> cu.getRole() == Role.ADMIN && !cu.isBlocked() && !cu.isBanned())
                .map(CommunityUser::getUser).collect(Collectors.toSet());

        for (User admin : admins) {
            if (admin != null && admin.getEmail() != null && !admin.getEmail().equalsIgnoreCase(reporter)) {
                NotificationRequestDTO dto = NotificationRequestDTO.builder()
                        .email(admin.getEmail())
                        .senderEmail(reporter)
                        .type(NotificationType.SYSTEM_UPDATE)
                        .title("ChatRoom message reported")
                        .message("A message in chat room `" + chatRoomCode + "` was reported by " + reporter + ".")
                        .scope("community")
                        .referenceId(reportId)
                        .actionable(true)
                        .communityId(community.getId())
                            .build();
                notificationService.createNotification(dto);
            }
        }
    }

}
