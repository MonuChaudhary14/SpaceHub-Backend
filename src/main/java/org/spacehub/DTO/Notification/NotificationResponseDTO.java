package org.spacehub.DTO.Notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.spacehub.entities.Notification.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponseDTO {

  private UUID id;
  private UUID publicId;
  private String title;
  private String message;
  private NotificationType type;
  private String scope;
  private String senderName;
  private String senderEmail;
  private boolean actionable;
  private boolean read;
  private LocalDateTime createdAt;
  private String senderProfileImageUrl;

  private UUID communityId;
  private String communityName;

  private UUID referenceId;

}
