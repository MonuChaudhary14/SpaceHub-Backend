package org.spacehub.entities.VoiceRoom;

import jakarta.persistence.*;
import lombok.*;
import org.spacehub.entities.LocalGroup.LocalGroup;
import java.io.Serial;
import java.io.Serializable;


@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceRoom implements Serializable{

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String roomCode;

    private String name;

    private boolean active = true;

    @OneToOne(mappedBy = "voiceRoom",  cascade = CascadeType.ALL)
    private LocalGroup localGroup;

}
