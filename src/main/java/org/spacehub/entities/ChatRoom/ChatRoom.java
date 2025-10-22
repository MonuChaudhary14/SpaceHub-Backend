package org.spacehub.entities.ChatRoom;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String roomName;

    @Column(unique = true, nullable = false)
    private String roomCode;

    @Column(nullable = false)
    private String createdBy;

}
