package org.spacehub.service;

import org.spacehub.DTO.UserLocation;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LocationService {

  private final Map<String, UserLocation> activeUsers = new ConcurrentHashMap<>();

  public void updateLocation(String username, double lat, double lon) {
    activeUsers.compute(username, (k, old) -> {
      if (old == null) {
        return new UserLocation(username, lat, lon);
      }
      old.setLatitude(lat);
      old.setLongitude(lon);
      old.setLastActive(System.currentTimeMillis());
      return old;
    });
  }

  public void removeUser(String username) {
    activeUsers.remove(username);
  }

  public List<UserLocation> findNearby(double lat, double lon, double radiusKm) {
    List<UserLocation> nearby = new ArrayList<>();
    for (UserLocation u : activeUsers.values()) {
      double distance = distance(lat, lon, u.getLatitude(), u.getLongitude());
      if (distance <= radiusKm) {
        nearby.add(u);
      }
    }
    return nearby;
  }

  private double distance(double lat1, double lon1, double lat2, double lon2) {
    double R = 6371;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
      Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
        Math.sin(dLon/2) * Math.sin(dLon/2);
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  @Scheduled(fixedRate = 60000)
  public void removeInactiveUsers() {
    long now = System.currentTimeMillis();
    long inactiveThreshold = 5 * 60 * 1000;

    activeUsers.values().removeIf(user -> (now - user.getLastActive()) > inactiveThreshold);
  }
}
