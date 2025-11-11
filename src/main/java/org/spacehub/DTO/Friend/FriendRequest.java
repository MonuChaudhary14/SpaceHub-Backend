package org.spacehub.DTO.Friend;

import lombok.Data;

@Data
public class FriendRequest {

  private String userEmail;
  private String friendEmail;

}
