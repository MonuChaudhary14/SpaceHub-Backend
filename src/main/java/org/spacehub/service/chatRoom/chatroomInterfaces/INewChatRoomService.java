package org.spacehub.service.chatRoom.chatroomInterfaces;

import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.NewChatRoom;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface INewChatRoomService {

  ApiResponse<NewChatRoom> createNewChatRoom(String roomCode, String name);

  ApiResponse<List<NewChatRoom>> getAllNewChatRooms(String roomCode);

  ApiResponse<NewChatRoom> getNewChatRoomByCode(String newChatRoomCode);

  Optional<NewChatRoom> getEntityByCode(UUID roomCode);

  ApiResponse<List<Map<String, Object>>> getAllNewChatRoomsSummary(String roomCode);

  ApiResponse<String> deleteNewChatRoom(String newChatRoomCode, String parentRoomCode);

}
