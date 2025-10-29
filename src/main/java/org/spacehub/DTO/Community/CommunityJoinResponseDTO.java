package org.spacehub.DTO.Community;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CommunityJoinResponseDTO {
    private Long communityId;
    private String name;
    private String description;
    private String imageUrl;
    private List<String> chatRooms;
    private String message;
}
