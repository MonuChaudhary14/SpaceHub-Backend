package org.spacehub.repository;

import org.spacehub.entities.DirectMessaging.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

  List<Message> findBySenderEmailAndReceiverEmailOrReceiverEmailAndSenderEmailOrderByTimestampAsc(
          String sender, String receiver, String receiverAlt, String senderAlt
  );

  List<Message> findBySenderEmailOrReceiverEmailOrderByTimestampDesc(String email1, String email2);

  @Query("""
         SELECT DISTINCT 
           CASE 
             WHEN m.senderEmail = :email THEN m.receiverEmail 
             ELSE m.senderEmail 
           END
         FROM Message m
         WHERE m.senderEmail = :email OR m.receiverEmail = :email
         """)
  List<String> findDistinctChatPartners(String email);
}
