package org.spacehub.service.Interface;

import org.spacehub.DTO.CreateRoomRequest;
import org.spacehub.DTO.chatroom.LeaveRoomRequest;
import org.spacehub.DTO.chatroom.RoleChangeAction;
import org.spacehub.DTO.chatroom.RoomMemberAction;
import org.spacehub.DTO.chatroom.RoomResponseDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.ChatRoom;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IChatRoomService {

  Optional<ChatRoom> findByRoomCode(UUID roomCode);

  ApiResponse<RoomResponseDTO> createRoom(CreateRoomRequest requestDTO);

  ApiResponse<ChatRoom> getRoomByCodeData(String roomCode);

  ApiResponse<List<ChatRoom>> getAllRoomsData();

  ApiResponse<String> deleteRoomResponse(String roomCode, String userId);

  ApiResponse<String> joinRoomResponse(String roomCode, String userId);

  ApiResponse<String> removeMember(RoomMemberAction requestDTO);

  ApiResponse<String> changeRole(RoleChangeAction requestDTO);

  ApiResponse<String> leaveRoom(LeaveRoomRequest requestDTO);

  ApiResponse<List<ChatRoom>> getRoomsByCommunity(Long communityId);
}

