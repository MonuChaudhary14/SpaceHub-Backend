package org.spacehub.DTO.LocalGroup;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class LocalGroupJoinResponseDTO {
  private UUID groupId;
  private String name;
  private String description;
}
