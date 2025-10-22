package org.spacehub.DTO.chatroom;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LeaveRoomRequest {

    @NotBlank(message = "Room code is required")
    private String roomCode;

    @NotBlank(message = "User ID is required")
    private String userId;

}
