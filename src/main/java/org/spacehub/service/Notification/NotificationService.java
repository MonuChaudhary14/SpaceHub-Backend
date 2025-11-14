package org.spacehub.service.Notification;

import lombok.RequiredArgsConstructor;
import org.spacehub.entities.Notification.Notification;
import org.spacehub.entities.Notification.NotificationType;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.User.User;
import org.spacehub.DTO.Notification.NotificationRequestDTO;
import org.spacehub.DTO.Notification.NotificationResponseDTO;
import org.spacehub.handler.NotificationWebSocketHandler;
import org.spacehub.mapper.NotificationMapper;
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
@Transactional
public class NotificationService implements INotificationService {

  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;
  private final CommunityRepository communityRepository;
  private final NotificationWebSocketHandler notificationWebSocketHandler;
  private final NotificationMapper notificationMapper;

  @Override
  public void createNotification(NotificationRequestDTO request) {
    User recipient = fetchRecipient(request.getEmail());
    User sender = fetchSenderIfProvided(request.getSenderEmail());
    Community community = fetchCommunityIfProvided(request.getCommunityId());

    NotificationType type = request.getType();
    Template template = TEMPLATES.getOrDefault(type, DEFAULT_TEMPLATE);

    String senderName = Optional.ofNullable(sender)
      .map(User::getUsername)
      .orElse("Someone");

    String communityName = Optional.ofNullable(community)
      .map(Community::getName)
      .orElse(null);

    String communityPart = Optional.ofNullable(communityName)
      .map(name -> ": " + name)
      .orElse("");

    String title = Optional.ofNullable(request.getTitle())
      .orElseGet(() -> fillTemplate(template.titleTpl().replace("{communityPart}", communityPart),
        senderName, communityName));

    String message = Optional.ofNullable(request.getMessage())
      .orElseGet(() -> fillTemplate(template.messageTpl(), senderName, communityName));

    String scope = Optional.ofNullable(request.getScope()).orElse(template.scope());

    UUID referenceId = Optional.ofNullable(request.getReferenceId())
      .orElseGet(UUID::randomUUID);

    boolean actionable = request.isActionable() || Boolean.TRUE.equals(template.actionableDefault());

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
      .actionable(actionable)
      .createdAt(LocalDateTime.now())
      .expiresAt(LocalDateTime.now().plusDays(30))
      .read(false)
      .build();

    notificationRepository.save(notification);

    NotificationResponseDTO dto = notificationMapper.mapToDTO(notification);
    try {
      notificationWebSocketHandler.sendNotification(request.getEmail(), dto);
    } catch (Exception ignored) {}
  }

  private User fetchRecipient(String email) {
    return userRepository.findByEmail(email)
      .orElseThrow(() -> new RuntimeException("User not found: " + email));
  }

  private User fetchSenderIfProvided(String senderEmail) {
    if (senderEmail == null || senderEmail.isBlank()) {
      return null;
    }
    return userRepository.findByEmail(senderEmail)
      .orElseThrow(() -> new RuntimeException("Sender not found: " + senderEmail));
  }

  private Community fetchCommunityIfProvided(UUID communityId) {
    if (communityId == null) {
      return null;
    }
    return communityRepository.findById(communityId)
      .orElseThrow(() -> new RuntimeException("Community not found with ID: " + communityId));
  }

  private String fillTemplate(String tpl, String senderName, String communityName) {
    if (tpl == null) {
      return null;
    }
    String safeSender = Optional.ofNullable(senderName).orElse("Someone");
    String safeCommunity = Optional.ofNullable(communityName).orElse("the community");

    return tpl.replace("{sender}", safeSender)
      .replace("{community}", safeCommunity);
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
    if (start >= end) {
      return Collections.emptyList();
    }

    return list.subList(start, end).stream()
      .map(notificationMapper::mapToDTO)
      .collect(Collectors.toList());
  }

  @Override
  public List<NotificationResponseDTO> fetchAndMarkRead(String email, int page, int size) {

    List<Notification> all = notificationRepository.findAllByRecipientWithDetails(email);

    boolean changed = false;
    for (Notification n : all) {
      if (!n.isActionable() && !n.isRead()) {
        n.setRead(true);
        changed = true;
      }
    }
    if (changed) {
      notificationRepository.saveAll(all);
    }

    int start = page * size;
    int end = Math.min(start + size, all.size());
    if (start >= end) {
      return Collections.emptyList();
    }

    return all.subList(start, end).stream()
      .map(notificationMapper::mapToDTO)
      .collect(Collectors.toList());
  }

  @Override
  public void markAsRead(UUID id) {
    Notification notification = notificationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Notification not found"));

    if (!notification.isRead() && !notification.isActionable()) {
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
  @Transactional(readOnly = true)
  public long countUnreadNotifications(String email) {
    return notificationRepository.findByRecipientEmailOrderByCreatedAtDesc(email).stream()
      .filter(n -> !n.isRead()).count();
  }

  @Override
  public void deleteByPublicId(UUID publicId, String userEmail) {

    Notification notification = notificationRepository.findByPublicId(publicId)
            .orElseThrow(() -> new RuntimeException("Notification not found"));

    if (!notification.getRecipient().getEmail().equalsIgnoreCase(userEmail)) {
      throw new RuntimeException("You cannot delete another user's notification");
    }

    notificationRepository.deleteByPublicId(publicId);
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

  public void deleteActionableByReference(UUID referenceId) {
    if (referenceId == null) {
      return;
    }

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
      .referenceId(Optional.ofNullable(referenceId).orElseGet(UUID::randomUUID))
      .build();

    createNotification(request);
  }

  private record Template(String titleTpl, String messageTpl, String scope, Boolean actionableDefault) {}

  private static final Template DEFAULT_TEMPLATE =
    new Template("Notification", "You have a new notification.", "general",
      false);

  private static final Map<NotificationType, Template> TEMPLATES = Map.ofEntries(
    Map.entry(NotificationType.FRIEND_REQUEST,
      new Template("{sender} sent you a friend request",
        "{sender} wants to connect with you.",
        "friend", true)),
    Map.entry(NotificationType.FRIEND_ACCEPTED,
      new Template("{sender} accepted your friend request",
        "You and {sender} are now friends!",
        "friend", false)),
    Map.entry(NotificationType.FRIEND_REJECTED,
      new Template("{sender} rejected your friend request",
        "{sender} declined your friend request.", "friend", false)),
    Map.entry(NotificationType.COMMUNITY_INVITE,
      new Template("Community Invite{communityPart}",
        "{sender} invited you to join {community}", "community", true)),
    Map.entry(NotificationType.COMMUNITY_JOINED,
      new Template("{sender} joined the community", "{sender} is now a member of {community}",
        "community", false)),
    Map.entry(NotificationType.COMMUNITY_REQUEST_ACCEPTED,
      new Template("Community Join Request Accepted",
        "Your request to join {community} has been accepted.", "community",
        false)),
    Map.entry(NotificationType.COMMUNITY_INVITE_REVOKED,
      new Template("Community Invite Revoked",
        "Your invite to join {community} has been revoked.", "community",
        false)),
    Map.entry(NotificationType.COMMUNITY_MEMBER_LEFT,
      new Template("{sender} left the community", "{sender} left {community}",
        "community", false)),
    Map.entry(NotificationType.COMMUNITY_MEMBER_REMOVED,
      new Template("Removed from Community", "You have been removed from {community}",
        "community", false)),
    Map.entry(NotificationType.LOCAL_GROUP_INVITE,
      new Template("Local Group Invitation", "{sender} invited you to join a local group.",
        "local-group", true)),
    Map.entry(NotificationType.LOCAL_GROUP_JOIN,
      new Template("{sender} joined the local group",
        "{sender} is now part of the local group.", "local-group", false)),
    Map.entry(NotificationType.REPORT_MESSAGE_DIRECT,
      new Template("Direct Message Report Submitted",
        "A direct message report has been filed.",
        "report", false)),
    Map.entry(NotificationType.REPORT_MESSAGE_CHATROOM,
      new Template("Chatroom Message Report Submitted", "A chatroom message was reported.",
        "report", false)),
    Map.entry(NotificationType.REPORT_REVIEW,
      new Template("Your Report Has Been Reviewed",
        "Your report has been reviewed by moderators.", "report", false)),
    Map.entry(NotificationType.SYSTEM_UPDATE,
      new Template("System Update", "A system update is available.", "system",
        false))
  );

}
