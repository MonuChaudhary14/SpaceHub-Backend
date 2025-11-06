package org.spacehub.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.VoiceRoom.VoiceRoom;
import org.spacehub.repository.VoiceRoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VoiceRoomService {

    private static final Logger logger = LoggerFactory.getLogger(VoiceRoomService.class);

    private final JanusService janusService;
    private final VoiceRoomRepository voiceRoomRepository;

    @Transactional
    public VoiceRoom createVoiceRoom(ChatRoom chatRoom, String name, String createdBy) {
        voiceRoomRepository.findByNameAndChatRoom(name, chatRoom).ifPresent(r -> {
            throw new IllegalStateException("Voice room '" + name + "' already exists in this group");
        });

        String sessionId = janusService.createSession();
        String handleId = janusService.attachAudioBridgePlugin(sessionId);
        int janusRoomId = 1000 + new Random().nextInt(9000);

        janusService.createAudioRoom(sessionId, handleId, janusRoomId);

        VoiceRoom voiceRoom = VoiceRoom.builder()
                .janusRoomId(janusRoomId)
                .name(name)
                .createdBy(createdBy)
                .chatRoom(chatRoom)
                .build();

        voiceRoomRepository.save(voiceRoom);

        logger.info("Created voice room '{}' (janusId={}) for chatRoom '{}'",
                name, janusRoomId, chatRoom.getName());

        return voiceRoom;
    }

    @Transactional(readOnly = true)
    public List<VoiceRoom> getVoiceRoomsForChatRoom(ChatRoom chatRoom) {
        return voiceRoomRepository.findByChatRoom(chatRoom);
    }

    @Transactional
    public void deleteVoiceRoom(ChatRoom chatRoom, String roomName, String requester) {
        VoiceRoom room = voiceRoomRepository.findByNameAndChatRoom(roomName, chatRoom)
                .orElseThrow(() -> new RuntimeException("Voice room not found: " + roomName));

        if (!room.getCreatedBy().equals(requester)) {
            throw new RuntimeException("Only the creator can delete this voice room");
        }

        voiceRoomRepository.delete(room);
        logger.info("Deleted voice room '{}' from chatRoom '{}'", roomName, chatRoom.getName());
    }

    @Transactional(readOnly = true)
    public VoiceRoom getVoiceRoomById(UUID id) {
        return voiceRoomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Voice room not found with ID: " + id));
    }

}
