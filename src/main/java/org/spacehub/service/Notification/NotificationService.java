package org.spacehub.service.Notification;

import lombok.RequiredArgsConstructor;
import org.spacehub.entities.Notification.Notification;
import org.spacehub.entities.Notification.NotificationType;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.User.User;
import org.spacehub.DTO.Notification.NotificationRequestDTO;
import org.spacehub.DTO.Notification.NotificationResponseDTO;
import org.spacehub.handler.NotificationWebSocketHandler;
import org.spacehub.repository.Notification.NotificationRepository;
import org.spacehub.repository.User.UserRepository;
import org.spacehub.repository.community.CommunityRepository;
import org.spacehub.service.Interface.INotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService implements INotificationService {

  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;
  private final CommunityRepository communityRepository;
  private final NotificationWebSocketHandler notificationWebSocketHandler;

  public void createNotification(NotificationRequestDTO request) {

    User recipient = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new RuntimeException("User not found: " + request.getEmail()));

    User sender = null;
    if (request.getSenderEmail() != null) {
      sender = userRepository.findByEmail(request.getSenderEmail())
              .orElseThrow(() -> new RuntimeException("Sender not found: " + request.getSenderEmail()));
    }

    Community community = null;
    if (request.getCommunityId() != null) {
      community = communityRepository.findById(request.getCommunityId())
              .orElseThrow(() -> new RuntimeException("Community not found with ID: " + request.getCommunityId()));
    }

    String title = request.getTitle();
    String message = request.getMessage();

    NotificationType type = request.getType();

    if (type != null) {
      switch (type) {

        case FRIEND_REQUEST:
          if (sender == null) break;
          title = title != null ? title : sender.getUsername() + " sent you a friend request";
          message = message != null ? message : sender.getUsername() + " wants to connect with you.";
          break;

        case FRIEND_ACCEPTED:
          if (sender == null) break;
          title = title != null ? title : sender.getUsername() + " accepted your friend request";
          message = message != null ? message : "You and " + sender.getUsername() + " are now friends!";
          break;

        case FRIEND_REJECTED:
          if (sender == null) break;
          title = title != null ? title : sender.getUsername() + " rejected your request";
          message = message != null ? message : sender.getUsername() + " declined your friend request.";
          break;

        case COMMUNITY_INVITE:
          if (sender == null) break;
          title = title != null ? title :
                  "Community Invite" + (community != null ? ": " + community.getName() : "");
          message = message != null ? message : sender.getUsername() + " invited you to join this community.";
          break;

        case COMMUNITY_JOINED:
          if (sender == null || community == null) break;
          title = title != null ? title : sender.getUsername() + " joined the community";
          message = message != null ? message : sender.getUsername() + " is now a member of " + community.getName();
          break;

        case COMMUNITY_REQUEST_ACCEPTED:
          if (community == null) break;
          title = title != null ? title : "Community Join Request Accepted";
          message = message != null ? message : "Your request to join " + community.getName() + " has been accepted.";
          break;

        case COMMUNITY_INVITE_REVOKED:
          if (community == null) break;
          title = title != null ? title : "Community Invite Revoked";
          message = message != null ? message : "Your invite to join " + community.getName() + " has been revoked.";
          break;

        case COMMUNITY_MEMBER_LEFT:
          if (sender == null || community == null) break;
          title = title != null ? title : sender.getUsername() + " left the community";
          message = message != null ? message : sender.getUsername() + " is no longer a member of " + community.getName();
          break;

        case COMMUNITY_MEMBER_REMOVED:
          if (community == null) break;
          title = title != null ? title : "Removed from Community";
          message = message != null ? message : "You have been removed from " + community.getName();
          break;

        case LOCAL_GROUP_INVITE:
          if (sender == null) break;
          title = title != null ? title : "Local Group Invitation";
          message = message != null ? message : sender.getUsername() + " invited you to join a local group.";
          break;

        case LOCAL_GROUP_JOIN:
          if (sender == null) break;
          title = title != null ? title : sender.getUsername() + " joined the local group";
          message = message != null ? message : sender.getUsername() + " is now part of the local group.";
          break;

        case REPORT_MESSAGE_DIRECT:
          title = title != null ? title : "Direct Message Report Submitted";
          message = message != null ? message : "A direct message report has been filed.";
          break;

        case REPORT_MESSAGE_CHATROOM:
          title = title != null ? title : "Chatroom Message Report Submitted";
          message = message != null ? message : "A chatroom message was reported.";
          break;

        case REPORT_REVIEW:
          title = title != null ? title : "Your Report Has Been Reviewed";
          message = message != null ? message : "Your report has been reviewed by moderators.";
          break;

        case COMMUNITY:
          title = title != null ? title : "Community Update";
          break;

        case SYSTEM_UPDATE:
          title = title != null ? title : "System Update";
          message = message != null ? message : "A system update is available.";
          break;

        default:
          if (title == null) title = "Notification";
          if (message == null) message = "You have a new notification.";
      }
    }

    Notification notification = Notification.builder()
            .publicId(UUID.randomUUID())
            .title(title)
            .message(message)
            .type(request.getType())
            .recipient(recipient)
            .sender(sender)
            .community(community)
            .referenceId(request.getReferenceId() != null ? request.getReferenceId() : UUID.randomUUID())
            .scope(request.getScope())
            .actionable(request.isActionable())
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusDays(30))
            .read(false)
            .build();

    notificationRepository.save(notification);

    NotificationResponseDTO dto = mapToDTO(notification);
    notificationWebSocketHandler.sendNotification(request.getEmail(), dto);
  }


  @Override
  @Transactional
  public List<NotificationResponseDTO> getUserNotifications(String email, String scope, int page, int size) {

    List<Notification> list = notificationRepository.findAllByRecipientWithDetails(email);

    if (scope != null && !scope.isBlank()) {
      list = list.stream()
              .filter(n -> scope.equalsIgnoreCase(n.getScope()))
              .toList();
    }

    int start = page * size;
    int end = Math.min(start + size, list.size());

    if (start > end) return Collections.emptyList();

    return list.subList(start, end)
            .stream().map(this::mapToDTO).collect(Collectors.toList());
  }

  @Override
  @Transactional
  public List<NotificationResponseDTO> fetchAndMarkRead(String email, int page, int size) {

    List<Notification> all = notificationRepository.findAllByRecipientWithDetails(email);

    all.forEach(n -> {
      if (!n.isActionable()) n.setRead(true);});

    notificationRepository.saveAll(all);

    int start = page * size;
    int end = Math.min(start + size, all.size());

    if (start > end) return Collections.emptyList();

    return all.subList(start, end).stream().map(this::mapToDTO)
            .collect(Collectors.toList());
  }

  @Override
  @Transactional
  public void markAsRead(UUID id) {
    Notification notification = notificationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Notification not found"));

    if (!notification.isRead() && !notification.isActionable()) {
      notification.setRead(true);
      notificationRepository.save(notification);
    }
  }

  @Override
  @Transactional
  public void deleteNotification(UUID id) {
    if (!notificationRepository.existsById(id)) {
      throw new RuntimeException("Notification not found with ID: " + id);
    }
    notificationRepository.deleteById(id);
  }

  @Override
  public long countUnreadNotifications(String email) {
    return notificationRepository.findByRecipientEmailOrderByCreatedAtDesc(email)
            .stream().filter(n -> !n.isRead()).count();
  }

  @Override
  @Transactional
  public void deleteByPublicId(UUID publicId, String userEmail) {

    Notification notification = notificationRepository.findByPublicId(publicId)
            .orElseThrow(() -> new RuntimeException("Notification not found"));

    if (!notification.getRecipient().getEmail().equalsIgnoreCase(userEmail)) {
      throw new RuntimeException("You cannot delete another user's notification");
    }

    notificationRepository.deleteByPublicId(publicId);
  }

  private NotificationResponseDTO mapToDTO(Notification n) {
    return NotificationResponseDTO.builder()
            .id(n.getId())
            .publicId(n.getPublicId())
            .title(n.getTitle())
            .message(n.getMessage())
            .type(n.getType())
            .scope(n.getScope())
            .actionable(n.isActionable())
            .read(n.isRead())
            .createdAt(n.getCreatedAt())
            .communityId(n.getCommunity() != null ? n.getCommunity().getId() : null)
            .communityName(n.getCommunity() != null ? n.getCommunity().getName() : null)
            .referenceId(n.getReferenceId())
            .senderName(n.getSender() != null ? n.getSender().getUsername() : null)
            .senderEmail(n.getSender() != null ? n.getSender().getEmail() : null)
            .senderProfileImageUrl(n.getSender() != null ? n.getSender().getAvatarUrl() : null)
            .build();
  }

  public void sendFriendRequestNotification(User sender, User recipient) {
    NotificationRequestDTO request = NotificationRequestDTO.builder()
            .senderEmail(sender.getEmail())
            .email(recipient.getEmail())
            .type(NotificationType.FRIEND_REQUEST)
            .scope("friend")
            .actionable(true)
            .referenceId(UUID.randomUUID())
            .build();

    createNotification(request);
  }

  public void sendLocalGroupJoinNotification(User newMember, User inviter, UUID groupId) {
    NotificationRequestDTO request = NotificationRequestDTO.builder()
            .senderEmail(newMember.getEmail())
            .email(inviter.getEmail())
            .type(NotificationType.LOCAL_GROUP_JOIN)
            .scope("local-group")
            .actionable(false)
            .referenceId(groupId)
            .build();

    createNotification(request);
  }

}
