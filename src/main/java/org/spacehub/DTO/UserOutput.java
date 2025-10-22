package org.spacehub.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserOutput {

  private Long id;
  private String firstName;
  private String lastName;
  private String email;

}
