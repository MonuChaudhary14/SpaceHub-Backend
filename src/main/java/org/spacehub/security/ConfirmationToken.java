package org.spacehub.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import lombok.Data;
import org.spacehub.entities.User.User;

import java.time.LocalDateTime;

@Data
@Entity
public class ConfirmationToken {

  @SequenceGenerator(
    name = "confirmation_token_sequence",
    sequenceName = "confirmation_token_sequence",
    allocationSize = 1
  )
  @Id
  @GeneratedValue(
    strategy = GenerationType.SEQUENCE,
    generator = "confirmation_token_sequence"
  )
  private Long id;

  @Column(nullable = false)
  private String token;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime expiresAt;

  private LocalDateTime confirmedAt;

  @ManyToOne
  @JoinColumn(
    nullable = false,
    name = "user_id"
  )
  private User user;

  public ConfirmationToken() {
  }
}
