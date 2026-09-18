package org.spacehub.entities.LocalGroup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.spacehub.entities.Community.InviteStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "localgroup_invites")
public class LocalGroupInvite {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne
  @JoinColumn(name = "group_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private LocalGroup localGroup;

  private String inviterEmail;

  @Column(unique = true, nullable = false)
  private String inviteCode;

  private int maxUses;

  @Builder.Default
  private int uses = 0;

  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  private LocalDateTime expiresAt;

  @Enumerated(EnumType.STRING)
  private InviteStatus status;

  @PrePersist
  public void onCreate() {
    createdAt = LocalDateTime.now();
    expiresAt = createdAt.plusHours(72);
    maxUses = 100;
  }

}
