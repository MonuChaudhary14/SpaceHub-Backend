package org.spacehub.service;

import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.ChatRoomUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.repository.ChatRoomUserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ChatRoomUserService {

  private final ChatRoomUserRepository repository;

  public ChatRoomUserService(ChatRoomUserRepository repository) {
    this.repository = repository;
  }

  @CacheEvict(value = "roomMembers", key = "#room.id")
  public void addUserToRoom(ChatRoom room, String userId, Role role) {
    ChatRoomUser user = ChatRoomUser.builder().userId(userId).room(room).role(role).build();
    repository.save(user);
  }

  @CacheEvict(value = "roomMembers", key = "#room.id")
  public void removeUserFromRoom(ChatRoom room, String userId) {
    repository.deleteByRoomAndUserId(room, userId);
  }

  @Cacheable(value = "roomMembers", key = "#room.id")
  public List<ChatRoomUser> getMembers(ChatRoom room) {
    return repository.findByRoom(room);
  }

  @Cacheable(value = "roomUser", key = "#room.id + '_' + #userId")
  public Optional<ChatRoomUser> getUserInRoom(ChatRoom room, String userId) {
    return repository.findByRoomAndUserId(room, userId);
  }

  @CachePut(value = "roomUser", key = "#user.room.id + '_' + #user.userId")
  @CacheEvict(value = "roomMembers", key = "#user.room.id")
  public void saveUser(ChatRoomUser user) {
    repository.save(user);
  }

}
