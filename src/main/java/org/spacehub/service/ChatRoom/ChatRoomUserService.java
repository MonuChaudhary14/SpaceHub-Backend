package org.spacehub.service.ChatRoom;

import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.ChatRoomUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.repository.ChatRoom.ChatRoomUserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ChatRoomUserService {

    private final ChatRoomUserRepository repository;

    public ChatRoomUserService(ChatRoomUserRepository repository) {
        this.repository = repository;
    }

    public void addUserToRoom(ChatRoom room, String userId, Role role) {
        ChatRoomUser user = ChatRoomUser.builder().userId(userId).room(room).role(role).build();
        repository.save(user);
    }

    public void removeUserFromRoom(ChatRoom room, String userId) {
        repository.deleteByRoomAndUserId(room, userId);
    }

    public List<ChatRoomUser> getMembers(ChatRoom room) {
        return repository.findByRoom(room);
    }

    public Optional<ChatRoomUser> getUserInRoom(ChatRoom room, String userId) {
        List<ChatRoomUser> users = repository.findByRoomAndUserId(room, userId);
        if (users.isEmpty()) return Optional.empty();
        return Optional.of(users.get(0));
    }

    public void saveUser(ChatRoomUser user) {
        repository.save(user);
    }

}
