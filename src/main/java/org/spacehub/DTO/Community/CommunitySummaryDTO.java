package org.spacehub.DTO.Community;

import lombok.Data;

@Data
public class CommunitySummaryDTO {
  private Long id;
  private String name;
  private String description;
  private String imageUrl;
}
