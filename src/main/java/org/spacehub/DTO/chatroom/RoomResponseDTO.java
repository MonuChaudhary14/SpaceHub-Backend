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
    private Long communityId;

    public static RoomResponseDTO fromEntity(ChatRoom room) {
        RoomResponseDTO dto = new RoomResponseDTO();
        dto.setRoomCode(room.getRoomCode());
        dto.setRoomName(room.getRoomName());
        dto.setCreatedBy(room.getCreatedBy());
        dto.setCommunityId(room.getCommunity().getId());
        return dto;
    }

}
