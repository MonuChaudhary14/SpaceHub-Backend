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

  @Override
  @Transactional
  public void createNotification(NotificationRequestDTO request) {

    User recipient = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new RuntimeException("User not found: " + request.getEmail()));

    User sender = null;
    if (request.getSenderEmail() != null && !request.getSenderEmail().isBlank()) {
      sender = userRepository.findByEmail(request.getSenderEmail())
              .orElseThrow(() -> new RuntimeException("Sender not found: " + request.getSenderEmail()));
    }

    Community community = null;
    if (request.getCommunityId() != null) {
      community = communityRepository.findById(request.getCommunityId())
              .orElseThrow(() -> new RuntimeException("Community not found with ID: " +
                request.getCommunityId()));
    }

    String title = request.getTitle();
    String message = request.getMessage();

    NotificationType type = request.getType();

    UUID referenceId = request.getReferenceId() != null ? request.getReferenceId() : UUID.randomUUID();

    String scope = request.getScope();

    String senderName = sender != null ? sender.getUsername() : "Someone";
    String communityName = community != null ? community.getName() : null;

    if (type != null) {
      switch (type) {

        case FRIEND_REQUEST:
          title = title != null ? title : senderName + " sent you a friend request";
          message = message != null ? message : senderName + " wants to connect with you.";
          if (scope == null) scope = "friend";
          break;

        case FRIEND_ACCEPTED:
          title = title != null ? title : senderName + " accepted your friend request";
          message = message != null ? message : "You and " + senderName + " are now friends!";
          if (scope == null) scope = "friend";
          break;

        case FRIEND_REJECTED:
          title = title != null ? title : senderName + " rejected your friend request";
          message = message != null ? message : senderName + " declined your friend request.";
          if (scope == null) scope = "friend";
          break;

        case COMMUNITY_INVITE:
          title = title != null ? title : "Community Invite" + (communityName != null ? ": " + communityName : "");
          message = message != null ? message : senderName + " invited you to join this community.";
          if (scope == null) scope = "community";
          break;

        case COMMUNITY_JOINED:
          title = title != null ? title : senderName + " joined the community";
          message = message != null ? message : (senderName + " is now a member of " + (communityName != null ?
            communityName : "the community"));
          if (scope == null) scope = "community";
          break;

        case COMMUNITY_REQUEST_ACCEPTED:
          title = title != null ? title : "Community Join Request Accepted";
          message = message != null ? message : "Your request to join " + (communityName != null ? communityName :
            "the community") + " has been accepted.";
          if (scope == null) scope = "community";
          break;

        case COMMUNITY_INVITE_REVOKED:
          title = title != null ? title : "Community Invite Revoked";
          message = message != null ? message : "Your invite to join " + (communityName != null ? communityName :
            "the community") + " has been revoked.";
          if (scope == null) scope = "community";
          break;

        case COMMUNITY_MEMBER_LEFT:
          title = title != null ? title : senderName + " left the community";
          message = message != null ? message : senderName + " is no longer a member of " +
            (communityName != null ? communityName : "the community");
          if (scope == null) scope = "community";
          break;

        case COMMUNITY_MEMBER_REMOVED:
          title = title != null ? title : "Removed from Community";
          message = message != null ? message : "You have been removed from " + (communityName != null ?
            communityName : "the community");
          if (scope == null) scope = "community";
          break;

        case LOCAL_GROUP_INVITE:
          title = title != null ? title : "Local Group Invitation";
          message = message != null ? message : senderName + " invited you to join a local group.";
          if (scope == null) scope = "local-group";
          break;

        case LOCAL_GROUP_JOIN:
          title = title != null ? title : senderName + " joined the local group";
          message = message != null ? message : senderName + " is now part of the local group.";
          if (scope == null) scope = "local-group";
          break;

        case REPORT_MESSAGE_DIRECT:
          title = title != null ? title : "Direct Message Report Submitted";
          message = message != null ? message : "A direct message report has been filed.";
          if (scope == null) scope = "report";
          break;

        case REPORT_MESSAGE_CHATROOM:
          title = title != null ? title : "Chatroom Message Report Submitted";
          message = message != null ? message : "A chatroom message was reported.";
          if (scope == null) scope = "report";
          break;

        case REPORT_REVIEW:
          title = title != null ? title : "Your Report Has Been Reviewed";
          message = message != null ? message : "Your report has been reviewed by moderators.";
          if (scope == null) scope = "report";
          break;

        case SYSTEM_UPDATE:
          title = title != null ? title : "System Update";
          message = message != null ? message : "A system update is available.";
          if (scope == null) scope = "system";
          break;

        case COMMUNITY:
        default:
          if (title == null) title = "Notification";
          if (message == null) message = "You have a new notification.";
          if (scope == null) scope = "general";
      }
    } else {
      if (title == null) title = "Notification";
      if (message == null) message = "You have a new notification.";
      if (scope == null) scope = "general";
    }

    Notification notification = Notification.builder()
            .publicId(UUID.randomUUID())
            .title(title)
            .message(message)
            .type(request.getType())
            .recipient(recipient)
            .sender(sender)
            .community(community)
            .referenceId(referenceId)
            .scope(scope)
            .actionable(request.isActionable())
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusDays(30))
            .read(false)
            .build();

    notificationRepository.save(notification);

    NotificationResponseDTO dto = mapToDTO(notification);
    try {
      notificationWebSocketHandler.sendNotification(request.getEmail(), dto);
    } catch (Exception ignored) {
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<NotificationResponseDTO> getUserNotifications(String email, String scope, int page, int size) {

    List<Notification> list = notificationRepository.findAllByRecipientWithDetails(email);

    if (scope != null && !scope.isBlank()) {
      list = list.stream()
              .filter(n -> scope.equalsIgnoreCase(n.getScope()))
              .toList();
    }

    int start = page * size;
    int end = Math.min(start + size, list.size());
    if (start >= end) return Collections.emptyList();

    return list.subList(start, end).stream().map(this::mapToDTO).collect(Collectors.toList());
  }

  @Override
  @Transactional
  public List<NotificationResponseDTO> fetchAndMarkRead(String email, int page, int size) {

    List<Notification> all = notificationRepository.findAllByRecipientWithDetails(email);

    boolean changed = false;
    for (Notification n : all) {
      if (!n.isActionable() && !n.isRead()) {
        n.setRead(true);
        changed = true;
      }
    }
    if (changed) notificationRepository.saveAll(all);

    int start = page * size;
    int end = Math.min(start + size, all.size());
    if (start >= end) return Collections.emptyList();

    return all.subList(start, end).stream().map(this::mapToDTO).collect(Collectors.toList());
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
  @Transactional(readOnly = true)
  public long countUnreadNotifications(String email) {
    return notificationRepository.findByRecipientEmailOrderByCreatedAtDesc(email).stream()
      .filter(n -> !n.isRead()).count();
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

  @Transactional
  public void deleteActionableByReference(UUID referenceId) {
    if (referenceId == null) return;

    notificationRepository.deleteActionableByReference(referenceId);
  }

  @Override
  public void sendFriendRequestNotification(User sender, User recipient) {
    sendFriendRequestNotification(sender, recipient, UUID.randomUUID());
  }

  public void sendFriendRequestNotification(User sender, User recipient, UUID referenceId) {
    NotificationRequestDTO request = NotificationRequestDTO.builder()
      .senderEmail(sender.getEmail())
      .email(recipient.getEmail())
      .type(NotificationType.FRIEND_REQUEST)
      .scope("friend")
      .actionable(true)
      .referenceId(referenceId != null ? referenceId : UUID.randomUUID())
      .build();

    createNotification(request);
  }


}
