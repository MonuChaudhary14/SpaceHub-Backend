package org.spacehub.DTO.Friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FriendUpdateDTO {

  private UUID id;
  private String firstName;
  private String lastName;
  private String email;
  private String avatarUrl;

}
