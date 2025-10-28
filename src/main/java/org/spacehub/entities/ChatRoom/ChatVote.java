package org.spacehub.entities.ChatRoom;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatVote {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String userId;

  @ManyToOne
  @JoinColumn(name = "poll_id")
  private ChatPoll poll;

  private int optionIndex;

}
