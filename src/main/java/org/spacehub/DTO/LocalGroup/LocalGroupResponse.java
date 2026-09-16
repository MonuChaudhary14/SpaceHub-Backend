package org.spacehub.DTO.LocalGroup;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class LocalGroupResponse {
  private UUID id;
  private String name;
  private String description;
  private String createdByEmail;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private int totalMembers;
  private List<String> memberEmails;
  private String imageUrl;
  private String chatRoomCode;
  private String imageKey;
  private UUID chatRoomId;

  public String getAvatarUrl() {
    return imageUrl;
  }

  public String getBannerUrl() {
    return imageUrl;
  }
}
