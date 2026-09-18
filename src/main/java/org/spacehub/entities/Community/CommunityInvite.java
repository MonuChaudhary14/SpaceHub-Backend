package org.spacehub.entities.Community;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;


@Data
@Entity
@Table(name = "community_invites")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityInvite {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID communityId;

  @Column(nullable = false)
  private String inviterEmail;

  @Column(unique = true, nullable = false)
  private String inviteCode;

  @Builder.Default
  private int maxUses = 10;
  @Builder.Default
  private int uses = 0;
  private LocalDateTime expiresAt;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  private InviteStatus status = InviteStatus.ACTIVE;

  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  private UUID notificationReference;

}
