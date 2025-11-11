package org.spacehub.DTO.Friend;

import lombok.Data;

@Data
public class RespondFriendRequest {

  private String userEmail;
  private String requesterEmail;
  private boolean accept;

}
