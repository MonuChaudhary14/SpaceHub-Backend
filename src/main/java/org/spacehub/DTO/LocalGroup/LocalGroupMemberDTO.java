package org.spacehub.DTO.LocalGroup;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalGroupMemberDTO {
  @Id
  @GeneratedValue
  @Column(columnDefinition = "UUID")
  private UUID id;
  private String username;
  private String email;
  private String avatarKey;
  private String avatarPreviewUrl;

}
