package org.spacehub.DTO.Community;

import lombok.Data;

@Data
public class CommunityChangeRoleRequest {

    private Long communityId;
    private String targetUserEmail;
    private String requesterEmail;
    private String newRole;

}
