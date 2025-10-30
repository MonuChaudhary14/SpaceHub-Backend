package org.spacehub.DTO.LocalGroup;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class LocalGroupResponse {
  private Long id;
  private String name;
  private String description;
  private String createdByEmail;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private int totalMembers;
  private List<String> memberEmails;
}
