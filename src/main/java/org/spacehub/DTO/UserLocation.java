package org.spacehub.DTO;

import lombok.Data;

@Data
public class UserLocation {

  private String username;
  private double latitude;
  private double longitude;
  private long lastActive;

  public UserLocation(String username, double latitude, double longitude) {
    this.username = username;
    this.latitude = latitude;
    this.longitude = longitude;
    this.lastActive = System.currentTimeMillis();
  }

}
