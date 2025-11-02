package org.spacehub.DTO.LocalGroup;

import lombok.Data;

import java.util.UUID;

@Data
public class DeleteLocalGroupRequest {
  private UUID groupId;
  private String requesterEmail;
}
