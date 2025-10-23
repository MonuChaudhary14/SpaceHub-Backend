package org.spacehub.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserLocation {
  private String username;
  private double latitude;
  private double longitude;
}
