//package org.spacehub.controller;
//
//import org.spacehub.DTO.UserLocation;
//import org.spacehub.entities.UserLocationEntity;
//import org.spacehub.service.LocationService;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//import java.util.stream.Collectors;
//
//@RestController
//@RequestMapping("/api")
//public class NearbyController {
//
//  private final LocationService locationService;
//
//  public NearbyController(LocationService locationService) {
//    this.locationService = locationService;
//  }
//
//  @PostMapping("/update-location")
//  public void updateLocation(@RequestParam String username,
//                             @RequestParam double lat,
//                             @RequestParam double lon) {
//    locationService.updateLocation(username, lat, lon);
//  }
//
//  @GetMapping("/nearby")
//  public List<UserLocation> findNearby(@RequestParam double lat,
//                                       @RequestParam double lon,
//                                       @RequestParam double radiusKm) {
//    List<UserLocationEntity> entities = locationService.findNearby(lon, lat, radiusKm);
//
//    return entities.stream()
//      .map(e -> new UserLocation(
//        e.getUsername(),
//        e.getLocation().getY(),
//        e.getLocation().getX()
//      ))
//      .collect(Collectors.toList());
//  }
//}
