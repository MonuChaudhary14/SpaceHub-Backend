package org.spacehub.service.Interface;

import org.spacehub.DTO.Notification.NotificationRequestDTO;
import org.spacehub.DTO.Notification.NotificationResponseDTO;

import java.util.List;
import java.util.UUID;

public interface INotificationService {

    void createNotification(NotificationRequestDTO request);

    List<NotificationResponseDTO> getUserNotifications(String email, String scope, int page, int size);

    List<NotificationResponseDTO> fetchAndMarkRead(String email, int page, int size);

    void markAsRead(UUID id);

    void deleteNotification(UUID id);

    long countUnreadNotifications(String email);

}
