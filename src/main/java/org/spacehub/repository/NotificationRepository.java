package org.spacehub.repository;

import org.spacehub.entities.Notification.Notification;
import org.spacehub.entities.User.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  List<Notification> findByRecipientEmailOrderByCreatedAtDesc(String email);

  List<Notification> findByRecipientEmailAndReadFalseOrderByCreatedAtDesc(String email, Pageable pageable);

  List<Notification> findByRecipientEmailAndReadTrueOrderByCreatedAtDesc(String email, Pageable pageable);

  void deleteAllBySender(User sender);

  void deleteAllByRecipient(User recipient);
}
