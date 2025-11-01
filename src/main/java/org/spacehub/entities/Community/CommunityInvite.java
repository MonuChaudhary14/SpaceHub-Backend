package org.spacehub.entities.Community;

import jakarta.persistence.*;
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
  private UUID inviterId;

  @Column(unique = true, nullable = false)
  private String inviteCode;

  private String email;

  @Column(nullable = false)
  private int maxUses = 1;

  @Column(nullable = false)
  private int uses = 0;

  @Column(nullable = false)
  private LocalDateTime expiresAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private InviteStatus status = InviteStatus.ACTIVE;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

}
