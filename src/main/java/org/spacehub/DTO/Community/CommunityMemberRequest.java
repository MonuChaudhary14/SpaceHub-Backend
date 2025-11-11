package org.spacehub.DTO.Community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CommunityMemberRequest {

  @NotNull(message = "communityId is required")
  private UUID communityId;
  @NotBlank(message = "userEmail is required")
  private String userEmail;
  @NotBlank(message = "requesterEmail is required")
  private String requesterEmail;
}
