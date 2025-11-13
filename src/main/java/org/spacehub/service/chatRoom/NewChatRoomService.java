package org.spacehub.service.chatRoom;

import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.NewChatRoom;
import org.spacehub.repository.ChatRoom.ChatRoomRepository;
import org.spacehub.repository.ChatRoom.NewChatRoomRepository;
import org.spacehub.service.chatRoom.chatroomInterfaces.INewChatRoomService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NewChatRoomService implements INewChatRoomService {

  private final ChatRoomRepository chatRoomRepository;
  private final NewChatRoomRepository newChatRoomRepository;

  public NewChatRoomService(ChatRoomRepository chatRoomRepository, NewChatRoomRepository newChatRoomRepository) {
    this.chatRoomRepository = chatRoomRepository;
    this.newChatRoomRepository = newChatRoomRepository;
  }

  public ApiResponse<NewChatRoom> createNewChatRoom(String roomCode, String name) {
    Optional<ChatRoom> optionalChatRoom = chatRoomRepository.findByRoomCode(UUID.fromString(roomCode));
    if (optionalChatRoom.isEmpty()) {
      return new ApiResponse<>(404, "Group not found", null);
    }

    ChatRoom chatRoom = optionalChatRoom.get();

    NewChatRoom newChatRoom = NewChatRoom.builder()
      .name(name)
      .roomCode(UUID.randomUUID())
      .createdAt(System.currentTimeMillis())
      .chatRoom(chatRoom)
      .build();

    newChatRoomRepository.save(newChatRoom);

    return new ApiResponse<>(200, "New chat room created successfully", newChatRoom);
  }

  public ApiResponse<List<NewChatRoom>> getAllNewChatRooms(String roomCode) {
    Optional<ChatRoom> optionalChatRoom = chatRoomRepository.findByRoomCode(UUID.fromString(roomCode));
    if (optionalChatRoom.isEmpty()) {
      return new ApiResponse<>(404, "ChatRoom not found", null);
    }

    List<NewChatRoom> list = newChatRoomRepository.findByChatRoom(optionalChatRoom.get());
    return new ApiResponse<>(200, "Fetched new chat rooms", list);
  }

  public ApiResponse<NewChatRoom> getNewChatRoomByCode(String newChatRoomCode) {
    Optional<NewChatRoom> newChatRoom = newChatRoomRepository.findByRoomCode(UUID.fromString(newChatRoomCode));
    return newChatRoom.map(room -> new ApiResponse<>(200, "Fetched new chat room", room))
      .orElseGet(() -> new ApiResponse<>(404, "NewChatRoom not found", null));
  }

  public Optional<NewChatRoom> getEntityByCode(UUID roomCode) {
    return newChatRoomRepository.findByRoomCode(roomCode);
  }

  public ApiResponse<List<Map<String, Object>>> getAllNewChatRoomsSummary(String roomCode) {
    try {
      Optional<ChatRoom> optionalChatRoom = chatRoomRepository.findByRoomCode(UUID.fromString(roomCode));
      if (optionalChatRoom.isEmpty()) {
        return new ApiResponse<>(404, "ChatRoom not found", null);
      }

      List<NewChatRoom> list = newChatRoomRepository.findByChatRoom(optionalChatRoom.get());

      List<Map<String, Object>> out = list.stream().map(ncr -> {
        Map<String, Object> m = new HashMap<>();
        m.put("chatRoomCode", ncr.getRoomCode().toString());
        m.put("name", ncr.getName());
        return m;
      }).collect(Collectors.toList());

      return new ApiResponse<>(200, "Fetched new chat rooms summary", out);
    } catch (IllegalArgumentException e) {
      return new ApiResponse<>(400, "Invalid roomCode", null);
    } catch (Exception e) {
      return new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null);
    }
  }

  public ApiResponse<String> deleteNewChatRoom(String newChatRoomCode, String roomCode) {
    try {
      UUID roomUUID = UUID.fromString(roomCode);
      UUID newUUID = UUID.fromString(newChatRoomCode);

      Optional<ChatRoom> optionalChatRoom = chatRoomRepository.findByRoomCode(roomUUID);
      if (optionalChatRoom.isEmpty()) {
        return new ApiResponse<>(404, "Parent ChatRoom not found", null);
      }
      ChatRoom parentRoom = optionalChatRoom.get();

      Optional<NewChatRoom> optionalNewRoom = newChatRoomRepository.findByRoomCode(newUUID);
      if (optionalNewRoom.isEmpty()) {
        return new ApiResponse<>(404, "NewChatRoom not found", null);
      }
      NewChatRoom newChatRoom = optionalNewRoom.get();

      if (!newChatRoom.getChatRoom().getRoomCode().equals(roomUUID)) {
        return new ApiResponse<>(403, "This chat room does not belong to the provided parent room", null);
      }

      newChatRoomRepository.delete(newChatRoom);

      return new ApiResponse<>(200, "NewChatRoom deleted successfully", null);

    }
    catch (IllegalArgumentException e) {
      return new ApiResponse<>(400, "Invalid UUID format", null);
    }
    catch (Exception e) {
      return new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null);
    }
  }


}
