package org.spacehub.controller;

import jakarta.validation.Valid;
import org.spacehub.DTO.chatroom.*;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.service.ChatRoom.ChatRoomService;
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
    public ResponseEntity<ApiResponse<RoomResponseDTO>> createRoom(@Valid @RequestBody CreateRoomRequest requestDTO) {
        return ResponseEntity.status(200).body(chatRoomService.createRoom(requestDTO));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<ChatRoom>>> getAllRooms() {
        return ResponseEntity.ok(chatRoomService.getAllRoomsData());
    }

    @PostMapping("/getRoom")
    public ResponseEntity<ApiResponse<ChatRoom>> getRoomByCode(@Valid @RequestBody RoomRequestDTO requestDTO) {
        return ResponseEntity.status(200).body(chatRoomService.getRoomByCodeData(requestDTO.getRoomCode()));
    }

    @PostMapping("/deleteRoom")
    public ResponseEntity<ApiResponse<String>> deleteRoom(@Valid @RequestBody RoomRequestDTO requestDTO) {
        return ResponseEntity.status(200).body(chatRoomService.deleteRoomResponse(requestDTO.getRoomCode(), requestDTO.getUserId()));
    }

    @PostMapping("/join")
    public ResponseEntity<ApiResponse<String>> joinRoom(@Valid @RequestBody RoomRequestDTO requestDTO) {
        return ResponseEntity.status(200).body(chatRoomService.joinRoomResponse(requestDTO.getRoomCode(), requestDTO.getUserId()));
    }

    @PostMapping("/removeMember")
    public ResponseEntity<ApiResponse<String>> removeMember(@Valid @RequestBody RemoveMemberRequest requestDTO) {
        return ResponseEntity.status(200).body(chatRoomService.removeMember(requestDTO));
    }

    @PostMapping("/changeRole")
    public ResponseEntity<ApiResponse<String>> changeRole(@Valid @RequestBody ChangeRoleRequest requestDTO) {
        return ResponseEntity.status(200).body(chatRoomService.changeRole(requestDTO));
    }

    @PostMapping("/leave")
    public ApiResponse<String> leaveRoom(@RequestBody LeaveRoomRequest requestDTO) {
        return chatRoomService.leaveRoom(requestDTO);
    }

}
