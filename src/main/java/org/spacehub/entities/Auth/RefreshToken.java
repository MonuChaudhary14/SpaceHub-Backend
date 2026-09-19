package org.spacehub.entities.Auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.spacehub.entities.User.User;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
  name = "refresh_tokens",
  indexes = {
    @Index(name = "idx_refresh_token_value", columnList = "token", unique = true),
    @Index(name = "idx_refresh_token_family", columnList = "family_id"),
    @Index(name = "idx_refresh_token_user_revoked", columnList = "user_id, is_revoked")
  }
)
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 100)
  private String token;

  @ManyToOne
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 100)
  private String familyId;

  @Column(nullable = false)
  @Builder.Default
  private Boolean isRevoked = false;

  @Column(length = 100)
  private String replacedByToken;

  @Column(length = 100)
  private String ipAddress;

  @Column(length = 500)
  private String userAgent;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant expiresAt;

  public RefreshToken(User user, Instant createdAt, Instant expiresAt) {
    this.token = UUID.randomUUID().toString();
    this.familyId = UUID.randomUUID().toString();
    this.user = user;
    this.isRevoked = false;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  public RefreshToken(User user, String familyId, Instant createdAt, Instant expiresAt) {
    this.token = UUID.randomUUID().toString();
    this.familyId = familyId != null && !familyId.isBlank() ? familyId : UUID.randomUUID().toString();
    this.user = user;
    this.isRevoked = false;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }
}
