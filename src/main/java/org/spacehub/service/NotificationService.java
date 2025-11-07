package org.spacehub.service;

import lombok.RequiredArgsConstructor;
import org.spacehub.entities.Notification.NotificationType;
import org.spacehub.handler.NotificationWebSocketHandler;
import org.spacehub.service.Interface.INotificationService;
import org.spacehub.DTO.Notification.NotificationRequestDTO;
import org.spacehub.DTO.Notification.NotificationResponseDTO;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.Notification.Notification;
import org.spacehub.entities.User.User;
import org.spacehub.repository.NotificationRepository;
import org.spacehub.repository.UserRepository;
import org.spacehub.repository.community.CommunityRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService implements INotificationService {

  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;
  private final CommunityRepository communityRepository;
  private final NotificationWebSocketHandler notificationWebSocketHandler;

  @Override
  public void createNotification(NotificationRequestDTO request) {

    User recipient = userRepository.findByEmail(request.getEmail()).orElseThrow(() ->
      new RuntimeException("User not found: " + request.getEmail()));

    User sender = userRepository.findByEmail(request.getSenderEmail()).orElseThrow(() ->
      new RuntimeException("Sender not found: " + request.getSenderEmail()));

    Community community = null;
    if (request.getCommunityId() != null) {
      community = communityRepository.findById(request.getCommunityId()).orElseThrow(() ->
        new RuntimeException("Community not found with ID: " + request.getCommunityId()));
    }

    String title = request.getTitle();
    String message = request.getMessage();

    switch (request.getType()) {

      case FRIEND_ACCEPTED:
        title = sender.getUsername() + " accepted your friend request";
        message = "You are now friends with " + sender.getUsername();
        break;

      case COMMUNITY_INVITE:
        title = "Community Invite: " + (community != null ? community.getName() : "");
        message = sender.getUsername() + " invited you to join the community.";
        break;

      case LOCAL_GROUP_INVITE:
        title = "Local Group Invitation";
        message = sender.getUsername() + " invited you to join a local group.";
        break;

      case SYSTEM_UPDATE:
        title = "System Update";
        message = "New feature or announcement: " + request.getMessage();
        break;

      default:
        break;
    }

    Notification notification = Notification.builder()
            .title(title)
            .message(message)
            .type(request.getType())
            .recipient(recipient)
            .sender(sender)
            .community(community)
            .referenceId(request.getReferenceId())
            .scope(request.getScope())
            .actionable(request.isActionable())
            .createdAt(java.time.LocalDateTime.now())
            .read(false)
            .build();

    notificationRepository.save(notification);
    NotificationResponseDTO dto = mapToDTO(notification);
    notificationWebSocketHandler.sendNotification(request.getEmail(), dto);
  }


  @Override
  public List<NotificationResponseDTO> getUserNotifications(String email, String scope, int page, int size) {
    PageRequest pageable = PageRequest.of(page, size);

    List<Notification> unread = notificationRepository
            .findByRecipientEmailAndReadFalseOrderByCreatedAtDesc(email, pageable);

    List<Notification> all = new ArrayList<>(unread);
    if (unread.size() < size) {
      int remaining = size - unread.size();
      List<Notification> read = notificationRepository
              .findByRecipientEmailAndReadTrueOrderByCreatedAtDesc(email,
                PageRequest.of(0, remaining));
      all.addAll(read);
    }

    return all.stream().map(this::mapToDTO).collect(Collectors.toList());
  }

  @Override
  @Transactional
  public List<NotificationResponseDTO> fetchAndMarkRead(String email, int page, int size) {
    List<NotificationResponseDTO> notifications = getUserNotifications(email, "global", page, size);

    List<Notification> unread = notificationRepository.findByRecipientEmailAndReadFalseOrderByCreatedAtDesc(
      email, PageRequest.of(0, 100));

    unread.forEach(n -> n.setRead(true));
    notificationRepository.saveAll(unread);

    return notifications;
  }

  @Override
  public void markAsRead(UUID id) {
    Notification notification = notificationRepository.findById(id).orElseThrow(() ->
      new RuntimeException("Notification not found with ID: " + id));

    if (!notification.isRead()) {
      notification.setRead(true);
      notificationRepository.save(notification);
    }
  }

  @Override
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

  private NotificationResponseDTO mapToDTO(Notification n) {
    return NotificationResponseDTO.builder()
            .id(n.getId())
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
            .build();
  }

  public void sendFriendRequestNotification(User sender, User recipient) {
    NotificationRequestDTO request = NotificationRequestDTO.builder()
            .senderEmail(sender.getEmail())
            .email(recipient.getEmail())
            .type(NotificationType.FRIEND_REQUEST)
            .scope("friend")
            .actionable(true)
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
