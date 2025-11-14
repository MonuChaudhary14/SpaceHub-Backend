package org.spacehub.repository.Notification;

import org.spacehub.entities.Notification.Notification;
import org.spacehub.entities.User.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  List<Notification> findByRecipientEmailOrderByCreatedAtDesc(String email);

  List<Notification> findByRecipientEmailAndReadFalseOrderByCreatedAtDesc(String email, Pageable pageable);

  List<Notification> findByRecipientEmailAndReadTrueOrderByCreatedAtDesc(String email, Pageable pageable);

  List<Notification> findByRecipientEmailAndActionableTrueOrderByCreatedAtDesc(String email);

  void deleteAllBySender(User sender);

  void deleteAllByRecipient(User recipient);

  @Modifying
  @Query("DELETE FROM Notification n WHERE n.community.id = :communityId")
  void deleteByCommunityId(@Param("communityId") UUID communityId);

  Optional<Notification> findByPublicId(UUID publicId);

  @Modifying
  @Query("DELETE FROM Notification n WHERE n.publicId = :publicId")
  void deleteByPublicId(@Param("publicId") UUID publicId);

  @Modifying
  @Query("DELETE FROM Notification n WHERE n.expiresAt < CURRENT_TIMESTAMP")
  void deleteExpired();

}
