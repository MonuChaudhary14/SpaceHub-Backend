package org.spacehub.entities.VoiceRoom;

import jakarta.persistence.*;
import lombok.*;
import org.spacehub.entities.Group.Group;
import org.spacehub.entities.LocalGroup.LocalGroup;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceRoom implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  private String name;

  @Column(unique = true, nullable = false)
  private UUID roomCode;

  @ManyToOne
  @JoinColumn(name = "group_id")
  private Group group;

  @OneToOne(mappedBy = "voiceRoom")
  private LocalGroup localGroup;

  @OneToMany(mappedBy = "voiceRoom", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<VoiceRoomUser> users = new HashSet<>();
}
