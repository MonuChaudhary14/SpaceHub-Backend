package org.spacehub.entities.ScheduledMessage;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
public class ScheduledMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String roomCode;
  private String senderEmail;
  private String message;
  private LocalDateTime scheduledTime;
  private boolean sent = false;

}
