//package org.spacehub.repository;
//
//import org.spacehub.entities.UserLocationEntity;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Modifying;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;
//import org.springframework.stereotype.Repository;
//import java.util.List;
//
//@Repository
//public interface UserLocationRepository extends JpaRepository<UserLocationEntity, String> {
//
//  @Query(value = "SELECT * FROM user_location_entity u " +
//    "WHERE ST_DWithin(u.location::geography, ST_MakePoint(:lon, :lat)::geography, :radiusMeters)",
//    nativeQuery = true)
//  List<UserLocationEntity> findNearby(
//    @Param("lon") double lon,
//    @Param("lat") double lat,
//    @Param("radiusMeters") double radiusMeters
//  );
//
//  @Modifying
//  @Query("DELETE FROM UserLocationEntity u WHERE u.lastActive < :threshold")
//  void deleteInactiveUsers(@Param("threshold") long threshold);
//}
