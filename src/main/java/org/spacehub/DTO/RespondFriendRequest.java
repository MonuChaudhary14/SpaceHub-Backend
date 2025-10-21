package org.spacehub.DTO;

import lombok.Data;

@Data
public class RespondFriendRequest {

    private String userEmail;
    private String requesterEmail;
    private boolean accept;

}
