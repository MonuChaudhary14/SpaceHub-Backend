package org.spacehub.service.Report;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.Report.DirectMessageReportRequest;
import org.spacehub.DTO.Report.ChatRoomReportRequest;
import org.spacehub.DTO.Notification.NotificationRequestDTO;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.Community.CommunityUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.entities.Reports.ChatRoomMessageReport;
import org.spacehub.entities.Reports.DirectMessageReport;
import org.spacehub.entities.Reports.ReportStatus;
import org.spacehub.entities.User.User;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.Notification.NotificationType;
import org.spacehub.repository.Reports.ChatRoomMessageReportRepository;
import org.spacehub.repository.Reports.DirectMessageReportRepository;
import org.spacehub.repository.community.CommunityRepository;
import org.spacehub.service.Interface.IReportService;
import org.spacehub.service.Notification.NotificationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService implements IReportService {

  private final DirectMessageReportRepository directReport;
  private final ChatRoomMessageReportRepository chatReport;
  private final CommunityRepository communityRepository;
  private final NotificationService notificationService;

  @Override
  public ApiResponse<Map<String, Object>> reportDirectMessage(DirectMessageReportRequest req) {

    DirectMessageReport report = DirectMessageReport.builder()
            .messageId(req.getMessageId())
            .reporterEmail(req.getReporterEmail())
            .senderEmail(req.getSenderEmail())
            .receiverEmail(req.getReceiverEmail())
            .reason(Optional.ofNullable(req.getReason()).orElse("No reason provided"))
            .status(ReportStatus.PENDING)
            .reportedAt(LocalDateTime.now())
            .build();

    DirectMessageReport saved = directReport.save(report);

    return new ApiResponse<>(200, "Direct message reported successfully",
            Map.of(
                    "reportId", saved.getId(),
                    "messageId", saved.getMessageId(),
                    "status", saved.getStatus().toString())
    );
  }

  @Override
  public ApiResponse<Map<String, Object>> reportChatRoomMessage(ChatRoomReportRequest req) {

    ChatRoomMessageReport report = ChatRoomMessageReport.builder()
            .messageId(req.getMessageId())
            .reporterEmail(req.getReporterEmail())
            .senderEmail(req.getSenderEmail())
            .chatRoomCode(req.getChatRoomCode())
            .communityCode(req.getCommunityCode())
            .reason(Optional.ofNullable(req.getReason()).orElse("No reason provided"))
            .status(ReportStatus.PENDING)
            .reportedAt(LocalDateTime.now())
            .build();

    ChatRoomMessageReport saved = chatReport.save(report);

    if (req.getCommunityCode() != null) {
      handleCommunityNotification(report, saved.getId());
    }

    return new ApiResponse<>(200, "Chat room message reported successfully",
            Map.of(
                    "reportId", saved.getId(),
                    "messageId", saved.getMessageId(),
                    "status", saved.getStatus().toString()
            )
    );
  }

  private void handleCommunityNotification(ChatRoomMessageReport report, UUID reportId) {
    try {
      UUID communityId = UUID.fromString(report.getCommunityCode());

      communityRepository.findByCommunityCode(communityId)
              .ifPresent(community -> notifyOwnersAndAdmins(report, community, reportId));

    }
    catch (IllegalArgumentException e) {
      System.err.println("Invalid communityCode UUID: " + report.getCommunityCode());
    }
  }

  private void notifyOwnersAndAdmins(ChatRoomMessageReport report, Community community, UUID reportId) {
    String reporterEmail = report.getReporterEmail();
    String chatRoomCode = report.getChatRoomCode();

    Set<User> owners = community.getCommunityUsers().stream()
            .filter(cu -> cu.getRole() == Role.WORKSPACE_OWNER && !cu.isBlocked() && !cu.isBanned())
            .map(CommunityUser::getUser)
            .collect(Collectors.toSet());

    if (owners.isEmpty() && community.getCreatedBy() != null) {
      owners.add(community.getCreatedBy());
    }

    notifyUsers(owners, reporterEmail, reportId, chatRoomCode, community, true);

    Set<User> admins = community.getCommunityUsers().stream()
            .filter(cu -> cu.getRole() == Role.ADMIN && !cu.isBlocked() && !cu.isBanned())
            .map(CommunityUser::getUser)
            .collect(Collectors.toSet());

    notifyUsers(admins, reporterEmail, reportId, chatRoomCode, community, false);
  }

  private void notifyUsers(Set<User> users, String reporter, UUID reportId,
                           String chatRoomCode, Community community, boolean isOwner) {

    for (User user : users) {
      if (user == null || user.getEmail() == null)
          continue;

      if (!isOwner && user.getEmail().equalsIgnoreCase(reporter))
          continue;

      NotificationRequestDTO dto = NotificationRequestDTO.builder()
              .email(user.getEmail())
              .senderEmail(reporter)
              .type(NotificationType.SYSTEM_UPDATE)
              .title(isOwner ? "Message reported in your workspace" : "ChatRoom message reported")
              .message(isOwner ?
                      "A message in chat room `" + chatRoomCode + "` was reported by " + reporter +
                        ". Please review it."
                      :
                      "A message in chat room `" + chatRoomCode + "` was reported by " + reporter + ".")
              .scope("community")
              .referenceId(reportId)
              .actionable(true)
              .communityId(community.getId())
              .build();

      notificationService.createNotification(dto);
    }
  }

}
