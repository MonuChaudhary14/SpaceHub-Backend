package org.spacehub.controller.VideoRoom;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.VideoRoom.VideoRoomDTO;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.VideoRoom.VideoRoom;
import org.spacehub.repository.ChatRoom.ChatRoomRepository;
import org.spacehub.service.Interface.IVideoRoomService;
import org.spacehub.service.VideoRoom.JanusVideoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/video-room")
@RequiredArgsConstructor
public class VideoCallController {

    private final JanusVideoService janusVideoService;
    private final IVideoRoomService videoRoomService;
    private final ChatRoomRepository chatRoomRepository;

    @PostMapping("/create")
    public ResponseEntity<?> createVideoRoom(@RequestParam UUID chatRoomId, @RequestParam String roomName, @RequestParam String createdBy) {

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("ChatRoom not found"));

        VideoRoom videoRoom = videoRoomService.createVideoRoom(chatRoom, roomName, createdBy);

        return ResponseEntity.ok(Map.of(
                "message", "Video room created successfully",
                "videoRoom", new VideoRoomDTO(videoRoom)));
    }

    @PostMapping("/join")
    public ResponseEntity<?> joinVideoRoom(@RequestParam int janusRoomId, @RequestParam String displayName) {

        String sessionId = janusVideoService.createSession();
        String handleId = janusVideoService.attachVideoRoomPlugin(sessionId);
        janusVideoService.joinVideoRoom(sessionId, handleId, janusRoomId, displayName);

        return ResponseEntity.ok(Map.of(
                "message", "Joined video room successfully",
                "janusRoomId", janusRoomId,
                "sessionId", sessionId,
                "handleId", handleId));
    }

    @GetMapping("/list/{chatRoomId}")
    public ResponseEntity<?> listVideoRooms(@PathVariable UUID chatRoomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("ChatRoom not found"));

        List<VideoRoom> rooms = videoRoomService.getVideoRoomsForChatRoom(chatRoom);

        return ResponseEntity.ok(Map.of(
                "count", rooms.size(),
                "videoRooms", rooms
        ));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteVideoRoom(@RequestParam UUID chatRoomId, @RequestParam String roomName, @RequestParam String requester) {

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("ChatRoom not found"));

        videoRoomService.deleteVideoRoom(chatRoom, roomName, requester);

        return ResponseEntity.ok(Map.of("message", "Video room deleted successfully"));
    }

}
