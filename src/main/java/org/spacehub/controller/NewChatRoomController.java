package org.spacehub.controller;

import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.NewChatRoom;
import org.spacehub.service.chatRoom.NewChatRoomService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/new-chatroom")
public class NewChatRoomController {

  private final NewChatRoomService newChatRoomService;

  public NewChatRoomController(NewChatRoomService newChatRoomService) {
      this.newChatRoomService = newChatRoomService;
  }

  @PostMapping("/create")
  public ApiResponse<NewChatRoom> createNewChatRoom(
          @RequestParam String chatRoomCode,
          @RequestParam String name
  ) {
    return newChatRoomService.createNewChatRoom(chatRoomCode, name);
  }

  @GetMapping("/list")
  public ApiResponse<List<NewChatRoom>> getAllNewChatRooms(@RequestParam String chatRoomCode) {
    return newChatRoomService.getAllNewChatRooms(chatRoomCode);
  }

  @GetMapping("/{newChatRoomCode}")
  public ApiResponse<NewChatRoom> getNewChatRoomByCode(@PathVariable String newChatRoomCode) {
    return newChatRoomService.getNewChatRoomByCode(newChatRoomCode);
  }

}
