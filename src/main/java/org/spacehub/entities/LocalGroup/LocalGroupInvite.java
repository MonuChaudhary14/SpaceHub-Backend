package org.spacehub.entities.LocalGroup;

import jakarta.persistence.*;
import lombok.*;
import org.spacehub.entities.Community.InviteStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "localgroup_invites")
public class LocalGroupInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false)
    private LocalGroup localGroup;

    private String inviterEmail;

    @Column(unique = true, nullable = false)
    private String inviteCode;

    private int maxUses;

    private int uses = 0;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    private InviteStatus status;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
        expiresAt = createdAt.plusHours(72);
        maxUses = 100;
    }

}
