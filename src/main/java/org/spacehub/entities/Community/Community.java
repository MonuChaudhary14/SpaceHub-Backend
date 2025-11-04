package org.spacehub.entities.Community;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.spacehub.entities.Group.Group;
import org.spacehub.entities.User.User;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Data
@ToString
@Entity
public class Community {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  private String name;

  private String description;

  @ManyToOne
  @JoinColumn(name = "created_by")
  private User createdBy;

  @ManyToMany
  @JoinTable(
          name = "community_pending_requests",
          joinColumns = @JoinColumn(name = "community_id"),
          inverseJoinColumns = @JoinColumn(name = "user_id")
  )
  @ToString.Exclude
  private Set<User> pendingRequests = new HashSet<>();

  @ManyToMany
  @JoinTable(
          name = "community_members",
          joinColumns = @JoinColumn(name = "community_id"),
          inverseJoinColumns = @JoinColumn(name = "user_id")
  )
  private Set<User> members = new HashSet<>();

  @Column(name = "image_url")
  private String imageUrl;

  @Column(name="avatar_url")
  private String avatarUrl;

  @Column(name="banner_url")
  private String bannerUrl;

  @Column(name = "community_id", unique = true, updatable = false, nullable = false)
  private UUID communityId;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  @PrePersist
  public void prePersist() {
    if (communityId == null) {
      communityId = UUID.randomUUID();
    }
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  public void preUpdate() {
    updatedAt = LocalDateTime.now();
  }

  @OneToMany(mappedBy = "community", cascade = CascadeType.ALL, orphanRemoval = true)
  @JsonManagedReference
  private Set<Group> groups = new HashSet<>();

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Community that)) {
      return false;
    }
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @OneToMany(mappedBy = "community", cascade = CascadeType.ALL, orphanRemoval = true)
  @ToString.Exclude
  private Set<CommunityUser> communityUsers = new HashSet<>();

}
