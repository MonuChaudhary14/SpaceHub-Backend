package org.spacehub.DTO.LocalGroup;

import lombok.Data;

@Data
public class CreateLocalGroupRequest {
  private String name;
  private String description;
  private String creatorEmail;
}
