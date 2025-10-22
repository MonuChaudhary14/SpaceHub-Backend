package org.spacehub.DTO;

import lombok.Data;

@Data
public class PresignedRequestDTO {

  private String file;
  private String contentType;

}
