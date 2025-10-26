package org.spacehub.DTO.chatroom;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
public class RemoveMemberRequest {

    @NotBlank(message = "Room code is required")
    private String roomCode;

    @NotBlank(message = "Requester ID is required")
    private String requesterId;

    @NotBlank(message = "Target user ID is required")
    private String targetUserId;

}
