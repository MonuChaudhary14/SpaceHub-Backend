package org.spacehub.controller.ChatRoom;

import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.NewChatRoom;
import org.spacehub.service.chatRoom.chatroomInterfaces.INewChatRoomService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/new-chatroom")
public class NewChatRoomController {

  private final INewChatRoomService newChatRoomService;

  public NewChatRoomController(INewChatRoomService newChatRoomService) {
    this.newChatRoomService = newChatRoomService;
  }

  @PostMapping("/create")
  public ApiResponse<NewChatRoom> createNewChatRoom(
    @RequestParam(value = "roomCode", required = false) String roomCodeParam,
    @RequestParam(value = "name", required = false) String nameParam,
    @RequestBody(required = false) Map<String, String> body
  ) {
    String roomCode = roomCodeParam;
    String name = nameParam;
    if (body != null) {
      if (body.get("roomCode") != null) roomCode = body.get("roomCode");
      if (body.get("name") != null) name = body.get("name");
    }
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

  @DeleteMapping("/{newChatRoomCode}/delete")
  public ApiResponse<String> deleteNewChatRoom(@PathVariable String newChatRoomCode, @RequestParam("RoomCode") String RoomCode
  ) {
    return newChatRoomService.deleteNewChatRoom(newChatRoomCode, RoomCode);
  }

}
