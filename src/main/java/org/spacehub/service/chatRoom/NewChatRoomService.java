package org.spacehub.service.chatRoom;


import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.NewChatRoom;
import org.spacehub.repository.ChatRoom.ChatRoomRepository;
import org.spacehub.repository.ChatRoom.NewChatRoomRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NewChatRoomService {

  private final ChatRoomRepository chatRoomRepository;
  private final NewChatRoomRepository newChatRoomRepository;

  public NewChatRoomService(ChatRoomRepository chatRoomRepository, NewChatRoomRepository newChatRoomRepository) {
    this.chatRoomRepository = chatRoomRepository;
    this.newChatRoomRepository = newChatRoomRepository;
  }

  public ApiResponse<NewChatRoom> createNewChatRoom(String chatRoomCode, String name) {
    Optional<ChatRoom> optionalChatRoom = chatRoomRepository.findByRoomCode(UUID.fromString(chatRoomCode));
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
    chatRoom.getNewChatRooms().add(newChatRoom);
    chatRoomRepository.save(chatRoom);

    return new ApiResponse<>(200, "New chat room created successfully", newChatRoom);
  }

  public ApiResponse<List<NewChatRoom>> getAllNewChatRooms(String chatRoomCode) {
    Optional<ChatRoom> optionalChatRoom = chatRoomRepository.findByRoomCode(UUID.fromString(chatRoomCode));
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

}
