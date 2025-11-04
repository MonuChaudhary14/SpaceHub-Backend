package org.spacehub.entities.ChatRoom;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatPoll {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String question;

  @ElementCollection
  private List<String> options;

  @ManyToOne
  @JoinColumn(name = "room_id")
  private ChatRoom room;

  private Long timestamp;

}
