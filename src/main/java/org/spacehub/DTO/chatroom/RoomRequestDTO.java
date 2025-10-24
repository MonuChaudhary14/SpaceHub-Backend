package org.spacehub.DTO.chatroom;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomRequestDTO {

    private String roomCode;
    private String userId;
}
