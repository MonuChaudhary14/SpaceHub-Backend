package org.spacehub.DTO.LocalGroup;

import lombok.Data;

@Data
public class DeleteLocalGroupRequest {
  private Long groupId;
  private String requesterEmail;
}
