package org.spacehub.service.Interface;

import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.VideoRoom.VideoRoom;

import java.util.List;

public interface IVideoRoomService {

    VideoRoom createVideoRoom(ChatRoom chatRoom, String name, String createdBy);

    List<VideoRoom> getVideoRoomsForChatRoom(ChatRoom chatRoom);

    void deleteVideoRoom(ChatRoom chatRoom, String roomName, String requester);
}
