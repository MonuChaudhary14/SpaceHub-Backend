package org.spacehub.DTO.VideoRoom;

import lombok.Data;
import org.spacehub.entities.VideoRoom.VideoRoom;

@Data
public class VideoRoomDTO {

    private int janusRoomId;
    private String name;
    private String createdBy;

    public VideoRoomDTO(VideoRoom entity) {
        this.janusRoomId = entity.getJanusRoomId();
        this.name = entity.getName();
        this.createdBy = entity.getCreatedBy();
    }
}
