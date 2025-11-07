package org.spacehub.service;

import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.ChatRoomUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.repository.ChatRoom.ChatRoomUserRepository;
import org.spacehub.service.Interface.IChatRoomUserService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChatRoomUserService implements IChatRoomUserService {

  private final ChatRoomUserRepository chatRoomUserRepository;

  public ChatRoomUserService(ChatRoomUserRepository chatRoomUserRepository) {
    this.chatRoomUserRepository = chatRoomUserRepository;
  }

  public void addUserToRoom(ChatRoom room, String email, Role role) {
    if (getUserInRoom(room, email).isEmpty()) {
      ChatRoomUser roomUser = new ChatRoomUser();
      roomUser.setRoom(room);
      roomUser.setEmail(email);
      roomUser.setRole(role);
      chatRoomUserRepository.save(roomUser);
    }
  }

  public void removeUserFromRoom(ChatRoom room, String email) {
    chatRoomUserRepository.deleteByRoomAndEmail(room, email);
  }

  public List<ChatRoomUser> getMembers(ChatRoom room) {
    return chatRoomUserRepository.findByRoom(room);
  }

  public Optional<ChatRoomUser> getUserInRoom(ChatRoom room, String email) {
    return chatRoomUserRepository.findByRoomAndEmail(room, email);
  }

  public void saveUser(ChatRoomUser user) {
    chatRoomUserRepository.save(user);
  }

  public List<ChatRoomUser> getMembersByRoomCode(UUID roomCode) {
    return chatRoomUserRepository.findByRoom_RoomCode(roomCode);
  }


}
