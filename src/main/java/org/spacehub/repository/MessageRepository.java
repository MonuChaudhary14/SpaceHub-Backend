package org.spacehub.repository;

import org.spacehub.entities.DirectMessaging.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
  List<Message> findBySenderIdAndReceiverIdOrReceiverIdAndSenderId(
    String senderId, String receiverId, String receiverId2, String senderId2
  );
}
