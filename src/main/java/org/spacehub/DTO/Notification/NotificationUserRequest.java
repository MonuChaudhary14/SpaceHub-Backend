package org.spacehub.DTO.Notification;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationUserRequest {
  private String scope = "global";
  private int page = 0;
  private int size = 20;
}
