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

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;

    public void createNotification(NotificationRequestDTO request) {
        User recipient = userRepository.findByEmail(request.getEmail()).orElseThrow(() -> new RuntimeException("User not found: " + request.getEmail()));

        Community community = null;
        if (request.getCommunityId() != null) {
            community = communityRepository.findById(request.getCommunityId()).orElseThrow(() -> new RuntimeException("Community not found with ID: " + request.getCommunityId()));
        }

        Notification notification = Notification.builder()
                .title(request.getTitle())
                .message(request.getMessage())
                .type(request.getType())
                .recipient(recipient)
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
                        .build()).toList();
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