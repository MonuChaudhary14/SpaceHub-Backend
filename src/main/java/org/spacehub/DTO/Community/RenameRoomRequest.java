package org.spacehub.DTO.Community;

import lombok.Data;

@Data
public class RenameRoomRequest {
  private String requesterEmail;
  private String newRoomName;
}
