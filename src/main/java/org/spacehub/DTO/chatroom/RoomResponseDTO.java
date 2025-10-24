package org.spacehub.DTO.chatroom;

import lombok.*;
import org.spacehub.entities.ChatRoom.ChatRoom;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomResponseDTO {

    private String roomCode;
    private String roomName;
    private String createdBy;

    public static RoomResponseDTO fromEntity(ChatRoom room) {
        return RoomResponseDTO.builder()
                .roomCode(room.getRoomCode())
                .roomName(room.getRoomName())
                .createdBy(room.getCreatedBy())
                .build();
    }

}
