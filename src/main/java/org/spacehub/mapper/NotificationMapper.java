package org.spacehub.mapper;

import org.spacehub.DTO.Notification.NotificationResponseDTO;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.Notification.Notification;
import org.spacehub.entities.User.User;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class NotificationMapper {

  public NotificationResponseDTO mapToDTO(Notification n) {
    if (n == null) {
      return null;
    }

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
      .communityId(Optional.ofNullable(n.getCommunity())
                            .map(Community::getId)
                            .orElse(null))
      .communityName(Optional.ofNullable(n.getCommunity())
                              .map(Community::getName)
                              .orElse(null))
      .referenceId(n.getReferenceId())
      .senderName(Optional.ofNullable(n.getSender())
                          .map(User::getUsername)
                          .orElse(null))
      .senderEmail(Optional.ofNullable(n.getSender())
                            .map(User::getEmail)
                            .orElse(null))
      .senderProfileImageUrl(Optional.ofNullable(n.getSender())
                                      .map(User::getAvatarUrl)
                                      .orElse(null))
      .build();
  }
}
