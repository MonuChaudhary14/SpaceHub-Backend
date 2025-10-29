//package org.spacehub.service;
//
//import org.locationtech.jts.geom.GeometryFactory;
//import org.locationtech.jts.geom.Point;
//import org.locationtech.jts.geom.Coordinate;
//import org.locationtech.jts.geom.PrecisionModel;
//import org.spacehub.entities.UserLocationEntity;
//import org.spacehub.repository.UserLocationRepository;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.List;
//
//@Service
//@Transactional
//public class LocationService {
//
//  private final UserLocationRepository repo;
//  private final GeometryFactory geometryFactory =
//    new GeometryFactory(new PrecisionModel(), 4326);
//
//  public LocationService(UserLocationRepository repo) {
//    this.repo = repo;
//  }
//
//  public void updateLocation(String username, double lat, double lon) {
//    Point point = geometryFactory.createPoint(new Coordinate(lon, lat));
//    UserLocationEntity entity =
//      new UserLocationEntity(username, point, System.currentTimeMillis());
//    repo.save(entity);
//  }
//
//  public void removeUser(String username) {
//    repo.deleteById(username);
//  }
//
//  public List<UserLocationEntity> findNearby(double lat, double lon, double radiusKm) {
//    double radiusMeters = radiusKm * 1000;
//    return repo.findNearby(lon, lat, radiusMeters);
//  }
//
//  @Scheduled(fixedRate = 60000)
//  public void removeInactiveUsers() {
//    long threshold = System.currentTimeMillis() - (5 * 60 * 1000);
//    repo.deleteInactiveUsers(threshold);
//  }
//
//
//}
