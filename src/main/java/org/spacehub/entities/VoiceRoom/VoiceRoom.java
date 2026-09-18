package org.spacehub.entities.VoiceRoom;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.spacehub.entities.ChatRoom.ChatRoom;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceRoom implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String createdBy;

  @Builder.Default
  private Instant createdAt = Instant.now();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "chat_room_id")
  @JsonBackReference
  private ChatRoom chatRoom;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "room_code", nullable = false)
  private String roomCode;

  @Builder.Default
  @Column(name = "room_type")
  private String roomType = "VOICE";

  @Builder.Default
  @Column(name = "janus_room_id")
  private Long janusRoomId = null;
}
