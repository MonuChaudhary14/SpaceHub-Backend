package org.spacehub.controller;

import org.spacehub.DTO.CreateRoomRequest;
import org.spacehub.DTO.RoomRequestDTO;
import org.spacehub.DTO.RoomResponseDTO;
import org.spacehub.entities.ApiResponse;
import org.spacehub.entities.ChatRoom;
import org.spacehub.entities.Role;
import org.spacehub.service.ChatRoomService;
import org.spacehub.service.ChatRoomUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("api/v1/rooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final ChatRoomUserService chatRoomUserService;

    public ChatRoomController(ChatRoomService chatRoomService, ChatRoomUserService chatRoomUserService) {
        this.chatRoomService = chatRoomService;
        this.chatRoomUserService = chatRoomUserService;
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<RoomResponseDTO>> createRoom(@RequestBody CreateRoomRequest requestDTO) {
        ChatRoom createdRoom = chatRoomService.createRoom(requestDTO.getName());

        chatRoomUserService.addUserToRoom(createdRoom, requestDTO.getUserId(), Role.ADMIN);

        RoomResponseDTO responseDTO = RoomResponseDTO.builder()
                .roomCode(createdRoom.getRoomCode())
                .name(createdRoom.getName())
                .message("Room created successfully")
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(200, "Room created Successfully", responseDTO));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<ChatRoom>>> getAllRooms() {
        List<ChatRoom> rooms = chatRoomService.getAllRooms();
        return ResponseEntity.ok(new ApiResponse<>(200, "Fetched all rooms", rooms));
    }

    @PostMapping("/getRoom")
    public ResponseEntity<ApiResponse<ChatRoom>> getRoomByCode(@RequestBody RoomRequestDTO requestDTO) {
        Optional<ChatRoom> optionalRoom = chatRoomService.findByRoomCode(requestDTO.getRoomCode());
        if (optionalRoom.isPresent()) {
            return ResponseEntity.ok(new ApiResponse<>(200, "Room fetched successfully", optionalRoom.get()));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, "Room not found", null));
        }
    }

    @PostMapping("/deleteRoom")
    public ResponseEntity<ApiResponse<String>> deleteRoom(@RequestBody RoomRequestDTO requestDTO) {
        boolean deleted = chatRoomService.deleteRoom(requestDTO.getRoomCode(), requestDTO.getUserId());

        if (deleted) {
            return ResponseEntity.ok(new ApiResponse<>(200, "Room deleted successfully",
                    "Room with code " + requestDTO.getRoomCode() + " deleted."));
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(403, "You are not authorized to delete this room", null));
        }
    }

}
