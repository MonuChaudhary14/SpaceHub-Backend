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

  @Override
  public void createNotification(NotificationRequestDTO request) {

    User recipient = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new RuntimeException("User not found: " + request.getEmail()));

    User sender = userRepository.findByEmail(request.getSenderEmail())
            .orElseThrow(() -> new RuntimeException("Sender not found: " + request.getSenderEmail()));

    Community community = null;
    if (request.getCommunityId() != null) {
      community = communityRepository.findById(request.getCommunityId())
              .orElseThrow(() -> new RuntimeException("Community not found with ID: " + request.getCommunityId()));
    }

    String title = request.getTitle();
    String message = request.getMessage();

    if (request.getType() != null) {
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
    }

    Notification notification = Notification.builder()
            .publicId(UUID.randomUUID())
            .title(title)
            .message(message)
            .type(request.getType())
            .recipient(recipient)
            .sender(sender)
            .community(community)
            .referenceId(request.getReferenceId())
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

    List<Notification> actionable = notificationRepository
            .findByRecipientEmailAndActionableTrueOrderByCreatedAtDesc(email);

    List<Notification> unread = notificationRepository
            .findByRecipientEmailAndReadFalseOrderByCreatedAtDesc(email, PageRequest.of(page, size));

    unread.forEach(n -> {
      if (!n.isActionable()) {
        n.setRead(true);
      }
    });
    notificationRepository.saveAll(unread);

    List<Notification> read = new ArrayList<>();
    if (unread.size() < size) {
      read = notificationRepository
              .findByRecipientEmailAndReadTrueOrderByCreatedAtDesc(email, PageRequest.of(0, size - unread.size()));
    }

    List<Notification> finalList = new ArrayList<>();
    finalList.addAll(actionable);
    finalList.addAll(unread);
    finalList.addAll(read);

    return finalList.stream().map(this::mapToDTO).collect(Collectors.toList());
  }

  @Override
  @Transactional
  public void markAsRead(UUID id) {
    Notification notification = notificationRepository.findById(id).orElseThrow(() ->
            new RuntimeException("Notification not found with ID: " + id));

    if (!notification.isRead()) {
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
