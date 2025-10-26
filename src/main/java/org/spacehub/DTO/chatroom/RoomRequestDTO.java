package org.spacehub.DTO.chatroom;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
public class RoomRequestDTO {
    @NotBlank
    private String roomCode;
    @NotBlank
    private String userId;
}
