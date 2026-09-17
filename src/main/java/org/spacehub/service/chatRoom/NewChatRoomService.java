package org.spacehub.service.chatRoom;

import lombok.RequiredArgsConstructor;
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

@RequiredArgsConstructor
@Service
public class NewChatRoomService implements INewChatRoomService {

  private final ChatRoomRepository chatRoomRepository;
  private final NewChatRoomRepository newChatRoomRepository;

  public ApiResponse<NewChatRoom> createNewChatRoom(String roomCode, String name) {
    if (roomCode == null || roomCode.isBlank()) {
      return new ApiResponse<>(400, "roomCode is required", null);
    }
    try {
      Optional<ChatRoom> optionalChatRoom = chatRoomRepository.findByRoomCode(UUID.fromString(roomCode));
      if (optionalChatRoom.isEmpty()) {
        return new ApiResponse<>(404, "Group not found", null);
      }

      ChatRoom chatRoom = optionalChatRoom.get();

      NewChatRoom newChatRoom = NewChatRoom.builder()
        .name(name != null && !name.isBlank() ? name : "general")
        .roomCode(UUID.randomUUID())
        .createdAt(System.currentTimeMillis())
        .chatRoom(chatRoom)
        .build();

      newChatRoomRepository.save(newChatRoom);

      return new ApiResponse<>(200, "New chat room created successfully", newChatRoom);
    } catch (IllegalArgumentException e) {
      return new ApiResponse<>(400, "Invalid UUID format for roomCode", null);
    }
  }

  public ApiResponse<List<NewChatRoom>> getAllNewChatRooms(String roomCode) {
    if (roomCode == null || roomCode.isBlank()) {
      return new ApiResponse<>(400, "roomCode is required", null);
    }
    try {
      Optional<ChatRoom> optionalChatRoom = chatRoomRepository.findByRoomCode(UUID.fromString(roomCode));
      if (optionalChatRoom.isEmpty()) {
        return new ApiResponse<>(404, "ChatRoom not found", null);
      }

      List<NewChatRoom> list = newChatRoomRepository.findByChatRoom(optionalChatRoom.get());
      return new ApiResponse<>(200, "Fetched new chat rooms", list);
    } catch (IllegalArgumentException e) {
      return new ApiResponse<>(400, "Invalid UUID format for roomCode", null);
    }
  }

  public ApiResponse<NewChatRoom> getNewChatRoomByCode(String newChatRoomCode) {
    try {
      Optional<NewChatRoom> newChatRoom = newChatRoomRepository.findByRoomCode(UUID.fromString(newChatRoomCode));

      return newChatRoom.map(room -> new ApiResponse<>(200, "Fetched new chat room",
        room))
        .orElseGet(() -> new ApiResponse<>(404, "NewChatRoom not found", null));

    } catch (IllegalArgumentException e) {
      return new ApiResponse<>(400, "Invalid UUID format for newChatRoomCode", null);
    } catch (Exception e) {
      return new ApiResponse<>(500, "Unexpected error: " + e.getMessage(), null);
    }
  }

  public Optional<NewChatRoom> getEntityByCode(UUID roomCode) {
    if (roomCode == null) {
      return Optional.empty();
    }
    Optional<NewChatRoom> directRoom = newChatRoomRepository.findByRoomCode(roomCode);
    if (directRoom.isPresent()) {
      return directRoom;
    }

    Optional<ChatRoom> parentGroup = chatRoomRepository.findByRoomCode(roomCode);
    if (parentGroup.isEmpty()) {
      parentGroup = chatRoomRepository.findById(roomCode);
    }

    if (parentGroup.isPresent()) {
      List<NewChatRoom> existing = newChatRoomRepository.findByChatRoom(parentGroup.get());
      if (!existing.isEmpty()) {
        return Optional.of(existing.get(0));
      }
      NewChatRoom defaultRoom = NewChatRoom.builder()
        .name("general")
        .roomCode(UUID.randomUUID())
        .createdAt(System.currentTimeMillis())
        .chatRoom(parentGroup.get())
        .build();
      return Optional.of(newChatRoomRepository.save(defaultRoom));
    }

    List<ChatRoom> communityRooms = chatRoomRepository.findByCommunityId(roomCode);
    if (!communityRooms.isEmpty()) {
      for (ChatRoom cr : communityRooms) {
        List<NewChatRoom> existing = newChatRoomRepository.findByChatRoom(cr);
        if (!existing.isEmpty()) {
          return Optional.of(existing.get(0));
        }
      }
      NewChatRoom defaultRoom = NewChatRoom.builder()
        .name("general")
        .roomCode(UUID.randomUUID())
        .createdAt(System.currentTimeMillis())
        .chatRoom(communityRooms.get(0))
        .build();
      return Optional.of(newChatRoomRepository.save(defaultRoom));
    }

    return Optional.empty();
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
