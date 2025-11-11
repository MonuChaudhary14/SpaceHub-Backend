package org.spacehub.DTO.Community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityBlockRequest {

  @NotNull(message = "communityId is required")
  private UUID communityId;
  @NotBlank(message = "targetUserEmail is required")
  private String targetUserEmail;
  @NotBlank(message = "requesterEmail is required")
  private String requesterEmail;
  private boolean block;

}
