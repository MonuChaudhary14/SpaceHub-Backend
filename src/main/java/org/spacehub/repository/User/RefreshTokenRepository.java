package org.spacehub.repository.User;

import org.spacehub.entities.Auth.RefreshToken;
import org.spacehub.entities.User.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByToken(String token);

  List<RefreshToken> findByFamilyId(String familyId);

  @Modifying
  @Query("UPDATE RefreshToken r SET r.isRevoked = true WHERE r.familyId = :familyId")
  void revokeFamily(@Param("familyId") String familyId);

  @Modifying
  @Query("UPDATE RefreshToken r SET r.isRevoked = true WHERE r.user.id = :userId")
  void revokeAllUserTokens(@Param("userId") UUID userId);

  void deleteAllByUser(User user);
}
