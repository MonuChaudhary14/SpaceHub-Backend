package org.spacehub.service.Notification;

import lombok.RequiredArgsConstructor;
import org.spacehub.repository.Notification.NotificationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

  private final NotificationRepository notificationRepository;

  @Scheduled(cron = "0 0 0 30 * *")
  @Transactional
  public void cleanupOldNotifications() {
    notificationRepository.deleteExpired();
    System.out.println("Old notifications cleaned up.");
  }

}
