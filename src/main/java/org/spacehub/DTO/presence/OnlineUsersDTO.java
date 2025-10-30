package org.spacehub.DTO.presence;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnlineUsersDTO {
  private Long communityId;
  private List<String> onlineEmails;
}
