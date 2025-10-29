package org.spacehub.DTO.Community;

import lombok.Data;

@Data
public class CommunitySearchResponseDTO {

    Long id;
    String name;
    String description;
    String creatorName;
    String imageUrl;

}
