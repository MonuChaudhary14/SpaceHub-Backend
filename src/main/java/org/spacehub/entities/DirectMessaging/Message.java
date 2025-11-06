package org.spacehub.entities.DirectMessaging;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "messages")
@NoArgsConstructor
public class Message {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String senderId;
  private String receiverId;
  private String content;
  private LocalDateTime timestamp;
  private boolean read;

}
