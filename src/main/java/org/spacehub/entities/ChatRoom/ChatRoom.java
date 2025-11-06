package org.spacehub.entities.ChatRoom;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.VoiceRoom.VoiceRoom;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
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

  @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
  @JsonManagedReference
  private List<NewChatRoom> newChatRooms = new ArrayList<>();

  @ManyToOne
  @JoinColumn(name = "community_id")
  @JsonBackReference
  private Community community;

  @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<VoiceRoom> voiceRooms = new ArrayList<>();

}

