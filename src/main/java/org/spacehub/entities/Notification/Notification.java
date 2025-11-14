package org.spacehub.entities.Notification;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

import org.spacehub.entities.User.User;
import org.spacehub.entities.Community.Community;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, updatable = false)
  private UUID publicId;

  private String title;

  @Column(length = 1000)
  private String message;

  @Enumerated(EnumType.STRING)
  private NotificationType type;

  private boolean read = false;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sender_id")
  private User sender;

  private LocalDateTime createdAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "recipient_id")
  private User recipient;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "community_id")
  private Community community;

  private UUID referenceId;

  private String scope;

  private boolean actionable = false;

  private LocalDateTime expiresAt;

  @PrePersist
  public void prePersist() {
    if (this.publicId == null) {
      this.publicId = UUID.randomUUID();
    }
    if (this.createdAt == null) {
      this.createdAt = LocalDateTime.now();
    }
    if (this.expiresAt == null) {
      this.expiresAt = LocalDateTime.now().plusDays(30);
    }
  }

}
