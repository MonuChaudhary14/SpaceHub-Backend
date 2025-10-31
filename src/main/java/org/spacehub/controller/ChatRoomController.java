package org.spacehub.controller;

import org.spacehub.DTO.CreateRoomRequest;
import org.spacehub.DTO.chatroom.LeaveRoomRequest;
import org.spacehub.DTO.chatroom.RoomRequestDTO;
import org.spacehub.DTO.chatroom.RoleChangeAction;
import org.spacehub.DTO.chatroom.RoomMemberAction;
import org.spacehub.DTO.chatroom.RoomResponseDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.service.ChatRoomService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/rooms")
public class ChatRoomController {

  private final ChatRoomService chatRoomService;

  public ChatRoomController(ChatRoomService chatRoomService) {
    this.chatRoomService = chatRoomService;
  }

  @PostMapping("/create")
  public ResponseEntity<ApiResponse<RoomResponseDTO>> createRoom(@RequestBody CreateRoomRequest requestDTO) {
    ApiResponse<RoomResponseDTO> response = chatRoomService.createRoom(requestDTO);
    return ResponseEntity.status(response.getStatus()).body(response);
  }

  @GetMapping("/all")
  public ResponseEntity<ApiResponse<List<ChatRoom>>> getAllRooms() {
    ApiResponse<List<ChatRoom>> response = chatRoomService.getAllRoomsData();
    return ResponseEntity.ok(response);
  }

  @PostMapping("/getRoom")
  public ResponseEntity<ApiResponse<ChatRoom>> getRoomByCode(@RequestBody RoomRequestDTO requestDTO) {
    ApiResponse<ChatRoom> response = chatRoomService.getRoomByCodeData(requestDTO.getRoomCode());
    return ResponseEntity.status(response.getStatus()).body(response);
  }

  @PostMapping("/deleteRoom")
  public ResponseEntity<ApiResponse<String>> deleteRoom(@RequestBody RoomRequestDTO requestDTO) {
    ApiResponse<String> response = chatRoomService.deleteRoomResponse(requestDTO.getRoomCode(), requestDTO.getUserId());
    return ResponseEntity.status(response.getStatus()).body(response);
  }

  @PostMapping("/join")
  public ResponseEntity<ApiResponse<String>> joinRoom(@RequestBody RoomRequestDTO requestDTO) {
    ApiResponse<String> response = chatRoomService.joinRoomResponse(requestDTO.getRoomCode(), requestDTO.getUserId());
    return ResponseEntity.status(response.getStatus()).body(response);
  }

  @PostMapping("/removeMember")
  public ResponseEntity<ApiResponse<String>> removeMember(@RequestBody RoomMemberAction requestDTO) {
    ApiResponse<String> response = chatRoomService.removeMember(requestDTO);
    return ResponseEntity.status(response.getStatus()).body(response);
  }

  @PostMapping("/changerole")
  public ResponseEntity<ApiResponse<String>> changeRole(@RequestBody RoleChangeAction requestDTO) {
    ApiResponse<String> response = chatRoomService.changeRole(requestDTO);
    return ResponseEntity.status(response.getStatus()).body(response);
  }

  @PostMapping("/leave")
  public ApiResponse<String> leaveRoom(@RequestBody LeaveRoomRequest requestDTO) {
    return chatRoomService.leaveRoom(requestDTO);
  }

  @PostMapping("/community/rooms")
  public ResponseEntity<ApiResponse<List<ChatRoom>>> getRoomsByCommunity(@RequestBody Long communityId) {
    return ResponseEntity.ok(chatRoomService.getRoomsByCommunity(communityId));
  }

}
