package org.spacehub.DTO.User;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserOutput {
  private UUID id;
  private String username;
  private String email;
  private String avatarUrl;

}
