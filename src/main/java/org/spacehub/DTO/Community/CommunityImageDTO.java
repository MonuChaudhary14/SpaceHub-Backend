package org.spacehub.DTO.Community;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class CommunityImageDTO {
    private Long communityId;
    private MultipartFile file;
}
