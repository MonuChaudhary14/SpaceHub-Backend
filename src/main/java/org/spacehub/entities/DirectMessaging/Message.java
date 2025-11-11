package org.spacehub.entities.DirectMessaging;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "direct_messages",
        indexes = {
                @Index(name = "idx_receiver_read_status", columnList = "receiverEmail, readStatus")
        }
)
public class Message {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 320)
  private String senderEmail;

  @Column(nullable = false, length = 320)
  private String receiverEmail;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "file_key", length = 2000)
  private String fileKey;

  @Column(length = 500)
  private String fileName;

  @Column(length = 255)
  private String contentType;

  @Column(nullable = false)
  private LocalDateTime timestamp;

  @Column(nullable = false, length = 50)
  private String type = "MESSAGE";

  @Builder.Default
  @Column(nullable = false)
  private Boolean readStatus = false;

  @Builder.Default
  private Boolean senderDeleted = false;

  @Builder.Default
  private Boolean receiverDeleted = false;

  private LocalDateTime deletedAt;
}