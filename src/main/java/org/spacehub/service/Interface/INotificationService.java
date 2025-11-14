package org.spacehub.service.Interface;

import org.spacehub.DTO.Notification.NotificationRequestDTO;
import org.spacehub.DTO.Notification.NotificationResponseDTO;
import org.spacehub.entities.User.User;

import java.util.List;
import java.util.UUID;

public interface INotificationService {

    void createNotification(NotificationRequestDTO request);

    List<NotificationResponseDTO> getUserNotifications(String email, String scope, int page, int size);

    List<NotificationResponseDTO> fetchAndMarkRead(String email, int page, int size);

    void markAsRead(UUID id);

    void deleteNotification(UUID id);

    void deleteByPublicId(UUID publicId, String userEmail);

    long countUnreadNotifications(String email);

    void sendFriendRequestNotification(User sender, User recipient);

    void sendLocalGroupJoinNotification(User newMember, User inviter, UUID groupId);

}
