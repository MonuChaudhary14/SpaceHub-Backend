package org.spacehub.repository;

import org.spacehub.entities.DirectMessaging.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

  List<Message> findBySenderEmailAndReceiverEmailOrReceiverEmailAndSenderEmail(
          String senderEmail, String receiverEmail, String receiverEmailAlt, String senderEmailAlt
  );
}
