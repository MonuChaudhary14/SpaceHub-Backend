package org.spacehub.entities.DirectMessaging;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
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
