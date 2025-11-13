package org.spacehub.entities.Friends;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.spacehub.entities.User.User;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@Entity
@Table(name = "friends")
public class Friends {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "friend_id", nullable = false)
  private User friend;

  @Column(nullable = false)
  private String status;

  private LocalDateTime createdAt = LocalDateTime.now();

}
