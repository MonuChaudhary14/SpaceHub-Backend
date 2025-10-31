package org.spacehub.repository;

import org.spacehub.entities.Notification.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientEmailAndScopeOrderByCreatedAtDesc(String email, String scope);

    boolean existsById(Long id);

}