package org.spacehub.entities.LocalGroup;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.spacehub.entities.User.User;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@ToString
@Entity
@Table(name = "local_groups")
public class LocalGroup {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;

  private String description;

  @Column(name = "image_url")
  private String imageUrl;

  @ManyToOne
  @JoinColumn(name = "created_by")
  private User createdBy;

  @ManyToMany
  @JoinTable(
    name = "localgroup_members",
    joinColumns = @JoinColumn(name = "group_id"),
    inverseJoinColumns = @JoinColumn(name = "user_id")
  )
  @ToString.Exclude
  private Set<User> members = new HashSet<>();

  private LocalDateTime createdAt = LocalDateTime.now();

  private LocalDateTime updatedAt = LocalDateTime.now();

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LocalGroup that)) {
      return false;
    }
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
