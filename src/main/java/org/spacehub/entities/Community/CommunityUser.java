package org.spacehub.entities.Community;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.spacehub.entities.User.User;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@Entity
@ToString
public class CommunityUser {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @ManyToOne
  @JoinColumn(name = "community_id", referencedColumnName = "community_id")
  @JsonIgnore
  private Community community;

  @ManyToOne
  @JoinColumn(name = "user_id")
  @ToString.Exclude
  private User user;

  @Enumerated(EnumType.STRING)
  private Role role;

  private LocalDateTime joinDate = LocalDateTime.now();

  private boolean isBanned = false;

  private boolean isBlocked = false;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CommunityUser that)) {
      return false;
    }
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

}
