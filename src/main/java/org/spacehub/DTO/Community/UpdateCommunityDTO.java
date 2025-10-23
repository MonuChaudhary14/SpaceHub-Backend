package org.spacehub.DTO.Community;

import lombok.Data;

@Data
public class UpdateCommunityDTO {

    private Long communityId;
    private String name;
    private String description;

}
