package org.spacehub.service;

import org.spacehub.entities.ChatRoom;
import org.spacehub.repository.ChatRoomRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;

    public ChatRoomService(ChatRoomRepository chatRoomRepository) {
        this.chatRoomRepository = chatRoomRepository;
    }

    public ChatRoom createRoom(String name){
        ChatRoom room = ChatRoom.builder().name(name).roomCode(UUID.randomUUID().toString()).build();
        return chatRoomRepository.save(room);
    }

    public Optional<ChatRoom> findByRoomCode(String roomCode){
        return chatRoomRepository.findByRoomCode(roomCode);
    }

}
