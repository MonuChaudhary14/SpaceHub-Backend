package org.spacehub.controller;

import org.spacehub.entities.ChatRoom;
import org.spacehub.service.ChatRoomService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("api/v1/rooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    public ChatRoomController(ChatRoomService chatRoomService) {
        this.chatRoomService = chatRoomService;
    }

    @PostMapping("/create")
    public ChatRoom createRoom(@RequestBody String name) {
        return chatRoomService.createRoom(name);
    }

    @GetMapping("/all")
    public List<ChatRoom> getAllRooms() {
        return chatRoomService.getAllRooms();
    }

    @GetMapping("/getRoom/{roomCode}")
    public Optional<ChatRoom> getRoomByCode(@PathVariable String roomCode) {
        return chatRoomService.findByRoomCode(roomCode);
    }

    @DeleteMapping("/delete/{roomCode}")
    public String deleteRoom(@PathVariable String roomCode) {
        boolean deleted = chatRoomService.deleteRoom(roomCode);
        return deleted ? "Room deleted successfully" : "Room not found";
    }

}
