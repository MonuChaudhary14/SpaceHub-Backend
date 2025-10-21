package org.spacehub.entities.Friends;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.spacehub.entities.User.User;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "friends")
public class Friends {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "friend_id", nullable = false)
    private User friend;

    @Column(nullable = false)
    private String status;

    private LocalDateTime createdAt = LocalDateTime.now();

}
