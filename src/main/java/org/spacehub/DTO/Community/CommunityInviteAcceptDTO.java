package org.spacehub.DTO.Community;

import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityInviteAcceptDTO {

    private UUID userId;
    private UUID communityId;
    private String inviteCode;
}
