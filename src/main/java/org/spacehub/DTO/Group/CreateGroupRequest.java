package org.spacehub.DTO.Group;

import java.util.UUID;

public class CreateGroupRequest {

    private UUID communityId;
    private String groupName;
    private String requesterEmail;

    public CreateGroupRequest() {}

    public CreateGroupRequest(UUID communityId, String groupName, String requesterEmail) {
        this.communityId = communityId;
        this.groupName = groupName;
        this.requesterEmail = requesterEmail;
    }

    public UUID getCommunityId() {
        return communityId;
    }

    public void setCommunityId(UUID communityId) {
        this.communityId = communityId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getRequesterEmail() {
        return requesterEmail;
    }

    public void setRequesterEmail(String requesterEmail) {
        this.requesterEmail = requesterEmail;
    }
}
