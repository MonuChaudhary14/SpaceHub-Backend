package org.spacehub.service;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.Notification.NotificationRequestDTO;
import org.spacehub.DTO.Notification.NotificationResponseDTO;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.Notification.Notification;
import org.spacehub.entities.User.User;
import org.spacehub.repository.NotificationRepository;
import org.spacehub.repository.UserRepository;
import org.spacehub.repository.community.CommunityRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;

    public void createNotification(NotificationRequestDTO request) {
        User recipient = userRepository.findByEmail(request.getEmail()).orElseThrow(() -> new RuntimeException("User not found: " + request.getEmail()));

        User sender = userRepository.findByEmail(request.getSenderEmail()).orElseThrow(() -> new RuntimeException("Sender not found: " + request.getSenderEmail()));

        Community community = null;
        if (request.getCommunityId() != null) {
            community = communityRepository.findById(request.getCommunityId()).orElseThrow(() -> new RuntimeException("Community not found with ID: " + request.getCommunityId()));
        }

        Notification notification = Notification.builder()
                .title(request.getTitle())
                .message(request.getMessage())
                .type(request.getType())
                .recipient(recipient)
                .sender(sender)
                .community(community)
                .referenceId(request.getReferenceId())
                .scope(request.getScope())
                .actionable(request.isActionable())
                .build();

        notificationRepository.save(notification);
    }

    public List<NotificationResponseDTO> getUserNotifications(String email, String scope) {
        List<Notification> notifications =
                notificationRepository.findByRecipientEmailAndScopeOrderByCreatedAtDesc(email, scope);

        return notifications.stream()
                .map(n -> NotificationResponseDTO.builder()
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
                        .build()).toList();
    }

    public List<NotificationResponseDTO> fetchAndMarkRead(String email) {
        List<Notification> allNotifications = notificationRepository.findByRecipientEmailOrderByCreatedAtDesc(email);

        List<Notification> unread = allNotifications.stream()
                .filter(notification -> !notification.isRead())
                .collect(Collectors.toList());

        List<Notification> read = allNotifications.stream()
                .filter(Notification::isRead)
                .collect(Collectors.toList());

        List<Notification> combined = unread.stream()
                .collect(Collectors.toList());


        combined.addAll(read);

        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);

        return combined.stream()
                .map(notification -> NotificationResponseDTO.builder()
                        .id(notification.getId())
                        .title(notification.getTitle())
                        .message(notification.getMessage())
                        .type(notification.getType())
                        .scope(notification.getScope())
                        .actionable(notification.isActionable())
                        .read(notification.isRead())
                        .createdAt(notification.getCreatedAt())
                        .communityId(notification.getCommunity() != null ? notification.getCommunity().getId() : null)
                        .communityName(notification.getCommunity() != null ? notification.getCommunity().getName() : null)
                        .referenceId(notification.getReferenceId())
                        .build())
                .collect(Collectors.toList());
    }

    public void markAsRead(Long id) {
        Notification notification = notificationRepository.findById(id).orElseThrow(() -> new RuntimeException("Notification not found with ID: " + id));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    public void deleteNotification(Long id) {
        if (!notificationRepository.existsById(id)) {
            throw new RuntimeException("Notification not found with ID: " + id);
        }
        notificationRepository.deleteById(id);
    }

}