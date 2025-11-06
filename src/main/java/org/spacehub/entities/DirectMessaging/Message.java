package org.spacehub.entities.DirectMessaging;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "direct_messages")
public class Message {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String senderEmail;

  @Column(nullable = false)
  private String receiverEmail;

  @Column(nullable = false, length = 1000)
  private String content;

  @Column(nullable = false)
  private LocalDateTime timestamp;

}
