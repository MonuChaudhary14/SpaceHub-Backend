package org.spacehub.entities.ApiResponse;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.io.Serial;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  private int status;
  private String message;
  @Nullable
  private T data;

  public ApiResponse(int status, String message) {
    this.status = status;
    this.message = message;
  }

}

