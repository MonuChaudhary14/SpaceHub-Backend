package org.spacehub.entities.ChatRoom;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "chat_messages")
public class ChatMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String senderEmail;

  @Column(length = 2000)
  private String message;

  private Long timestamp;

  private String fileName;
  private String fileUrl;
  private String contentType;

  private String roomCode;

  @Builder.Default
  private String type = "MESSAGE";

  @ManyToOne
  @JoinColumn(name = "new_chat_room_id")
  private NewChatRoom newChatRoom;

  @ManyToOne
  @JoinColumn(name = "room_id")
  private ChatRoom room;

}
