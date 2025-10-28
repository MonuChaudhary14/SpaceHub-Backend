package org.spacehub.DTO.Community;


import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityInviteRequestDTO {

    private UUID inviterId;
    private String email;
    private int maxUses = 1;
    private int expiresInHours = 72;

}
