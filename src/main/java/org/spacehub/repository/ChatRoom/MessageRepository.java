package org.spacehub.repository.ChatRoom;

import org.spacehub.entities.DirectMessaging.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

  @Query("""
      SELECT m
      FROM Message m
      WHERE 
          (m.senderEmail = :user1 AND m.receiverEmail = :user2)
          OR
          (m.senderEmail = :user2 AND m.receiverEmail = :user1)
      ORDER BY m.timestamp ASC
      """)
  List<Message> getChatAsc(String user1, String user2);

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

  Optional<Message> findByMessageUuid(String messageUuid);

  void deleteByMessageUuid(String messageUuid);

  List<Message> findByReceiverEmailAndReadStatusFalse(String receiverEmail);


  @Query("""
      SELECT COUNT(m)
      FROM Message m
      WHERE m.receiverEmail = :receiverEmail
        AND m.readStatus = false
      """)
  long countUnreadMessages(String receiverEmail);

  @Query("""
      SELECT COUNT(m)
      FROM Message m
      WHERE 
          m.senderEmail = :chatPartner
          AND m.receiverEmail = :userEmail
          AND m.readStatus = false
      """)
  long countUnreadMessagesInChat(String userEmail, String chatPartner);

  @Modifying
  @Query("""
      UPDATE Message m
      SET m.readStatus = true
      WHERE m.receiverEmail = :receiverEmail
        AND m.senderEmail = :senderEmail
        AND m.readStatus = false
      """)
  void markAllAsReadBetweenUsers(String receiverEmail, String senderEmail);

}
