package org.spacehub.DTO.LocalGroup;

import lombok.Data;

import java.util.UUID;

@Data
public class JoinLocalGroupRequest {
  private UUID groupId;
  private String userEmail;
}
