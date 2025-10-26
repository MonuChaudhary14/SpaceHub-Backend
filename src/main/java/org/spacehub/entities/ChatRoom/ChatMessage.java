package org.spacehub.entities.ChatRoom;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    private String senderId;

    @Column(length = 1000)
    private String message;

    private Long timestamp;

    @ManyToOne
    @JoinColumn(name = "room_id")
    @JsonIgnore
    private ChatRoom room;

    private String roomCode;

}
