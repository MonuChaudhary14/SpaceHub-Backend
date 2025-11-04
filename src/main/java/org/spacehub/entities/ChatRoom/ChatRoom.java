package org.spacehub.entities.ChatRoom;

import jakarta.persistence.*;
import lombok.*;
import org.spacehub.entities.Group.Group;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom implements Serializable {

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
}