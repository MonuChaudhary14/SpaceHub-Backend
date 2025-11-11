package org.spacehub.DTO.Files;

import lombok.Data;

@Data
public class PresignedRequestDTO {

  private String file;
  private String contentType;

}
