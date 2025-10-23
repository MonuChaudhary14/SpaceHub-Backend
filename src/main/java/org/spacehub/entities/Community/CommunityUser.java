package org.spacehub.entities.Community;

import jakarta.persistence.*;
import lombok.Data;
import org.spacehub.entities.User.User;

import java.time.LocalDateTime;

@Data
@Entity
public class CommunityUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "community_id")
    private Community community;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private Role role;

    private LocalDateTime joinDate = LocalDateTime.now();

    private boolean isBanned = false;

    private boolean isBlocked = false;

}
