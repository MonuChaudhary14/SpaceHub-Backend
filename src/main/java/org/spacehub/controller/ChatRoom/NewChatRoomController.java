package org.spacehub.controller.ChatRoom;

import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.NewChatRoom;
import org.spacehub.service.chatRoom.NewChatRoomService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/new-chatroom")
public class NewChatRoomController {

  private final NewChatRoomService newChatRoomService;

  public NewChatRoomController(NewChatRoomService newChatRoomService) {
    this.newChatRoomService = newChatRoomService;
  }

  @PostMapping("/create")
  public ApiResponse<NewChatRoom> createNewChatRoom(
    @RequestParam("roomCode") String roomCode,
    @RequestParam("name") String name
  ) {
    return newChatRoomService.createNewChatRoom(roomCode, name);
  }

  @GetMapping("/list")
  public ApiResponse<List<NewChatRoom>> getAllNewChatRooms(@RequestParam("roomCode") String roomCode) {
    return newChatRoomService.getAllNewChatRooms(roomCode);
  }

  @GetMapping("/{newChatRoomCode}")
  public ApiResponse<NewChatRoom> getNewChatRoomByCode(@PathVariable String newChatRoomCode) {
    return newChatRoomService.getNewChatRoomByCode(newChatRoomCode);
  }

  @GetMapping("/list/summary")
  public ApiResponse<List<Map<String, Object>>> getAllNewChatRoomsSummary(@RequestParam("roomCode") String roomCode) {
    return newChatRoomService.getAllNewChatRoomsSummary(roomCode);
  }

}
