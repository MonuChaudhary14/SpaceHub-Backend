package org.spacehub.DTO.Community;

import java.util.List;

public record CommunityPendingRequestDTO(
  Long communityId,
  String communityName,
  List<PendingRequestUserDTO> requests
) {
}
