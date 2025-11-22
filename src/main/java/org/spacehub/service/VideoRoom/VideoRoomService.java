package org.spacehub.service.VideoRoom;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.VideoRoom.VideoRoom;
import org.spacehub.repository.videoRoom.VideoRoomRepository;
import org.spacehub.service.Interface.IVideoRoomService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class VideoRoomService implements IVideoRoomService {

    private static final Logger logger = LoggerFactory.getLogger(VideoRoomService.class);

    private final JanusVideoService janusVideoService;
    private final VideoRoomRepository videoRoomRepository;

    @Transactional
    @Override
    public VideoRoom createVideoRoom(ChatRoom chatRoom, String name, String createdBy) {

        videoRoomRepository.findByNameAndChatRoom(name, chatRoom)
                .ifPresent(r -> {
                    throw new IllegalStateException("Video room '" + name + "' already exists");
                });

        String sessionId = janusVideoService.createSession();
        String handleId = janusVideoService.attachVideoRoomPlugin(sessionId);

        int janusRoomId = 2000 + new Random().nextInt(8000);

        janusVideoService.createVideoRoom(sessionId, handleId, janusRoomId, name);

        VideoRoom videoRoom = VideoRoom.builder()
                .name(name)
                .createdBy(createdBy)
                .janusRoomId(janusRoomId)
                .chatRoom(chatRoom)
                .active(true)
                .roomCode(String.valueOf(janusRoomId))
                .build();

        videoRoomRepository.save(videoRoom);

        logger.info("Created video room '{}' with Janus ID {}", name, janusRoomId);

        return videoRoom;
    }

    @Transactional(readOnly = true)
    @Override
    public List<VideoRoom> getVideoRoomsForChatRoom(ChatRoom chatRoom) {
        return videoRoomRepository.findByChatRoom(chatRoom);
    }

    @Transactional
    @Override
    public void deleteVideoRoom(ChatRoom chatRoom, String roomName, String requester) {

        VideoRoom videoRoom = videoRoomRepository
                .findByNameAndChatRoom(roomName, chatRoom)
                .orElseThrow(() -> new RuntimeException("Video room not found"));

        if (!videoRoom.getCreatedBy().equals(requester)) {
            throw new RuntimeException("Only the creator can delete this video room");
        }

        videoRoomRepository.delete(videoRoom);

        logger.info("Deleted video room '{}' from chatRoom '{}'", roomName, chatRoom.getName());
    }
}
