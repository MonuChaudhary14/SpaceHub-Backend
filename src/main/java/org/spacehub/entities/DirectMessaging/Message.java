package org.spacehub.entities.DirectMessaging;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "direct_messages",
        indexes = {
          @Index(name = "idx_receiver_read_status", columnList = "receiverEmail, readStatus"),
          @Index(name = "idx_sender_receiver_ts", columnList = "senderEmail, receiverEmail, timestamp"),
          @Index(name = "idx_message_uuid", columnList = "messageUuid")
        }
)
public class Message {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, updatable = false, length = 36)
  private String messageUuid;

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

  @PrePersist
  public void prePersist() {
    if (this.messageUuid == null) {
      this.messageUuid = UUID.randomUUID().toString();
    }
    if (this.timestamp == null) {
      this.timestamp = LocalDateTime.now();
    }
  }
}
