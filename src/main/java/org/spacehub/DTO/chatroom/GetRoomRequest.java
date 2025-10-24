package org.spacehub.DTO.chatroom;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetRoomRequest {

    @NotBlank(message = "Room code is required")
    private String roomCode;

    @NotBlank(message = "User ID is required")
    private String userId;


}
