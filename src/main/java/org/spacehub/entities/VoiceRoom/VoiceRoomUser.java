package org.spacehub.entities.VoiceRoom;

import jakarta.persistence.*;
import lombok.*;
import org.spacehub.entities.Community.Role;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceRoomUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    @ManyToOne
    @JoinColumn(name = "voice_room_id")
    private VoiceRoom voiceRoom;

    @Enumerated(EnumType.STRING)
    private Role role;
}
