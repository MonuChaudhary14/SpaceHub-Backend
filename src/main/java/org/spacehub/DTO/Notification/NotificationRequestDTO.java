package org.spacehub.DTO.Notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.spacehub.entities.Notification.NotificationType;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRequestDTO {

  private String email;
  private String title;
  private String message;
  private NotificationType type;
  private String senderEmail;
  private UUID communityId;
  private UUID referenceId;
  private String scope;
  private boolean actionable;

}