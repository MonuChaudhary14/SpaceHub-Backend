package org.spacehub.DTO.Community;

import java.util.List;
import java.util.UUID;

public record CommunityPendingRequestDTO(
  UUID communityId,
  String communityName,
  List<PendingRequestUserDTO> requests
) {
}
