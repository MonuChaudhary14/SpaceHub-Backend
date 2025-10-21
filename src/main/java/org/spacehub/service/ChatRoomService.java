package org.spacehub.service;

import org.spacehub.entities.ChatRoom;
import org.spacehub.entities.ChatRoomUser;
import org.spacehub.entities.Role;
import org.spacehub.repository.ChatMessageRepository;
import org.spacehub.repository.ChatRoomRepository;
import org.spacehub.repository.ChatRoomUserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomUserRepository chatRoomUserRepository;

    public ChatRoomService(ChatRoomRepository chatRoomRepository,
                           ChatMessageRepository chatMessageRepository,
                           ChatRoomUserRepository chatRoomUserRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatRoomUserRepository = chatRoomUserRepository;
    }

    public ChatRoom createRoom(String name){
        ChatRoom room = ChatRoom.builder().name(name).roomCode(UUID.randomUUID().toString()).build();
        return chatRoomRepository.save(room);
    }

    public Optional<ChatRoom> findByRoomCode(String roomCode) {
        return chatRoomRepository.findByRoomCode(roomCode);
    }

    public List<ChatRoom> getAllRooms() {
        return chatRoomRepository.findAll();
    }

    public boolean deleteRoom(String roomCode, String userId) {
        Optional<ChatRoom> OptionalRoom = chatRoomRepository.findByRoomCode(roomCode);
        if (OptionalRoom.isEmpty()) return false;

        ChatRoom room = OptionalRoom.get();

        Optional<ChatRoomUser> userRoomOpt = chatRoomUserRepository.findByRoomAndUserId(room, userId);

        if (userRoomOpt.isEmpty()) return false;

        Role role = userRoomOpt.get().getRole();
        if (role != Role.ADMIN && role != Role.WORKSPACE_OWNER) {
            return false;
        }

        chatMessageRepository.deleteAll(chatMessageRepository.findByRoomOrderByTimestampAsc(room));
        chatRoomUserRepository.deleteByRoom(room);
        chatRoomRepository.delete(room);

        return true;
    }

}
