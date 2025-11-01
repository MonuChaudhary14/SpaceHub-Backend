package org.spacehub.entities.LocalGroup;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.User.User;
import org.spacehub.entities.VoiceRoom.VoiceRoom;

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

  @NotBlank(message = "Local Group name cannot be empty")
  private String name;

  @NotBlank(message = "Description cannot be blank")
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

  @Column(name = "invite_code", unique = true)
  private String inviteCode;

  @OneToOne
  @JoinColumn(name = "voice_room_id")
  private VoiceRoom voiceRoom;

  @OneToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "chat_room_id", referencedColumnName = "id")
  private ChatRoom chatRoom;

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
