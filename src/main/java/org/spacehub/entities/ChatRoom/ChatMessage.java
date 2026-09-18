package org.spacehub.entities.ChatRoom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

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

  @Column(nullable = false, unique = true, updatable = false, length = 36)
  private String messageUuid;

  @Column(nullable = false, length = 320)
  private String senderEmail;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String message;

  @Column(nullable = false)
  private Long timestamp;

  @Column(length = 500)
  private String fileName;

  @Column(length = 2000)
  private String fileUrl;

  @Column()
  private String contentType;

  @Column(nullable = false, length = 100)
  private String roomCode;

  @Builder.Default
  @Column(nullable = false, length = 50)
  private String type = "MESSAGE";

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "new_chat_room_id")
  private NewChatRoom newChatRoom;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "room_id")
  private ChatRoom room;

  @PrePersist
  public void prePersist() {
    if (this.messageUuid == null) {
      this.messageUuid = UUID.randomUUID().toString();
    }
  }

}
