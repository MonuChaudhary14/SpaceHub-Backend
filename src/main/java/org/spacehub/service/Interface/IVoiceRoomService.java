package org.spacehub.service.Interface;

import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.VoiceRoom.VoiceRoom;

import java.util.List;

public interface IVoiceRoomService {

  VoiceRoom createVoiceRoom(ChatRoom chatRoom, String name, String createdBy);

  List<VoiceRoom> getVoiceRoomsForChatRoom(ChatRoom chatRoom);

  void deleteVoiceRoom(ChatRoom chatRoom, String roomName, String requester);

}
