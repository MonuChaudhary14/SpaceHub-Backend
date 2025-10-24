package org.spacehub.entities.Auth;

import jakarta.persistence.*;

import lombok.Data;
import org.spacehub.entities.User.User;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String token;

  @ManyToOne
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant expiresAt;

  public RefreshToken() {}

  public RefreshToken(User user, Instant createdAt, Instant expiresAt) {
    this.token = UUID.randomUUID().toString();
    this.user = user;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }
}
