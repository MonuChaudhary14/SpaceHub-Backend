package org.spacehub.entities.ChatRoom;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewChatRoom implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String name;

  @JsonProperty("chatRoomCode")
  @Column(unique = true, nullable = false)
  private UUID roomCode;

  private Long createdAt;

  @ManyToOne
  @JoinColumn(name = "chat_room_id")
  @JsonBackReference
  private ChatRoom chatRoom;
}
