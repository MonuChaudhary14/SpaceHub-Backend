package org.spacehub.entities.ChatRoom;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.spacehub.entities.Community.Community;

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

    @ManyToOne
    @JoinColumn(name = "community_id", nullable = false)
    @JsonIgnore
    private Community community;

}
