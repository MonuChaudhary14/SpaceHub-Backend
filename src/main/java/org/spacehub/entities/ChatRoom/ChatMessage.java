package org.spacehub.entities.ChatRoom;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String senderEmail;

  @Column(length = 1000)
  private String message;

  private Long timestamp;

  private String fileName;
  private String fileUrl;
  private String contentType;

  @ManyToOne
  @JoinColumn(name = "new_chat_room_id")
  private NewChatRoom newChatRoom;

  @ManyToOne
  @JoinColumn(name = "room_id")
  private ChatRoom room;

  private String roomCode;

}
