package org.spacehub.DTO.Community;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class CommunityJoinResponseDTO {
  private UUID communityId;
  private String name;
  private String description;
}

