package org.spacehub.repository.ChatRoom;

import org.spacehub.entities.ScheduledMessage.ScheduledMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScheduledMessageRepository extends JpaRepository<ScheduledMessage, Long>{

  List<ScheduledMessage> findBySentFalseAndScheduledTimeBefore(LocalDateTime time);

  void deleteAllBySenderEmail(String senderEmail);
}
