package org.spacehub.entities.ChatRoom;

import jakarta.persistence.*;
import lombok.*;
import org.spacehub.entities.Community.Role;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoomUser {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String email;

  @ManyToOne
  @JoinColumn(name = "room_id")
  private ChatRoom room;

  @Enumerated(EnumType.STRING)
  private Role role;

}
