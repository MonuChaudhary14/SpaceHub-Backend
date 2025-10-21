package org.spacehub.service;

import org.spacehub.entities.ChatRoom;
import org.spacehub.repository.ChatMessageRepository;
import org.spacehub.repository.ChatRoomRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatRoomService(ChatRoomRepository chatRoomRepository,
                           ChatMessageRepository chatMessageRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    public ChatRoom createRoom(String name){
        ChatRoom room = ChatRoom.builder().name(name).roomCode(UUID.randomUUID().toString()).build();
        return chatRoomRepository.save(room);
    }

    public Optional<ChatRoom> findByRoomCode(String roomCode){
        return chatRoomRepository.findByRoomCode(roomCode);
    }

    public List<ChatRoom> getAllRooms() {
        return chatRoomRepository.findAll();
    }

    public boolean deleteRoom(String roomCode) {
        Optional<ChatRoom> OptionalRoom = chatRoomRepository.findByRoomCode(roomCode);
        if (OptionalRoom.isPresent()) {
            ChatRoom room = OptionalRoom.get();

            chatMessageRepository.deleteAll(chatMessageRepository.findByRoomOrderByTimestampAsc(room));

            chatRoomRepository.delete(room);
            return true;
        }
        return false;
    }

}
