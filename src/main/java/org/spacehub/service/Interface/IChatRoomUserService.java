package org.spacehub.service.Interface;

import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.ChatRoomUser;
import org.spacehub.entities.Community.Role;

import java.util.List;
import java.util.Optional;

public interface IChatRoomUserService {

  void addUserToRoom(ChatRoom room, String userId, Role role);

  void removeUserFromRoom(ChatRoom room, String userId);

  List<ChatRoomUser> getMembers(ChatRoom room);

  Optional<ChatRoomUser> getUserInRoom(ChatRoom room, String userId);

  void saveUser(ChatRoomUser user);
}

