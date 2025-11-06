package org.spacehub.entities.VoiceRoom;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.spacehub.entities.ChatRoom.ChatRoom;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceRoom implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @Column(nullable = false, unique = true)
  private Integer janusRoomId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String createdBy;

  private Instant createdAt = Instant.now();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "chat_room_id")
  @JsonBackReference
  private ChatRoom chatRoom;
}
