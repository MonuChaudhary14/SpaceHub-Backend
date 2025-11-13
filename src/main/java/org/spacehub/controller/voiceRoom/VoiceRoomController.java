package org.spacehub.controller.voiceRoom;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.VoiceRoom.VoiceRoomDTO;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.VoiceRoom.VoiceRoom;
import org.spacehub.repository.ChatRoom.ChatRoomRepository;
import org.spacehub.service.Interface.IVoiceRoomService;
import org.spacehub.service.VoiceRoom.JanusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/voice-room")
@RequiredArgsConstructor
public class VoiceRoomController {

  private static final Logger logger = LoggerFactory.getLogger(VoiceRoomController.class);

  private final JanusService janusService;
  private final IVoiceRoomService voiceRoomService;
  private final ChatRoomRepository chatRoomRepository;

  @PostMapping("/create")
  public ResponseEntity<?> createVoiceRoom(
    @RequestParam UUID chatRoomId,
    @RequestParam String roomName,
    @RequestParam String createdBy) {

    try {
      ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
        .orElseThrow(() -> new RuntimeException("ChatRoom not found with ID: " + chatRoomId));

      VoiceRoom voiceRoom = voiceRoomService.createVoiceRoom(chatRoom, roomName, createdBy);

      VoiceRoomDTO voiceRoomDTO = new VoiceRoomDTO(voiceRoom);

      return ResponseEntity.ok(Map.of(
        "message", "Voice room created successfully",
        "voiceRoom", voiceRoomDTO
      ));
    } catch (Exception e) {
      logger.error("Error creating voice room: {}", e.getMessage(), e);
      return ResponseEntity.status(500)
        .body(Map.of("error", "Failed to create voice room", "message", e.getMessage()));
    }
  }

  @PostMapping("/join")
  public ResponseEntity<?> joinVoiceRoom(
          @RequestParam int janusRoomId,
          @RequestParam String displayName) {

    try {
      String sessionId = janusService.createSession();
      String handleId = janusService.attachAudioBridgePlugin(sessionId);
      janusService.joinAudioRoom(sessionId, handleId, janusRoomId, displayName);

      return ResponseEntity.ok(Map.of(
              "message", "Joined voice room successfully",
              "janusRoomId", janusRoomId,
              "sessionId", sessionId,
              "handleId", handleId
      ));
    }
    catch (Exception e) {
      logger.error("Error joining voice room: {}", e.getMessage(), e);
      return ResponseEntity.status(500)
              .body(Map.of("error", "Failed to join voice room", "message", e.getMessage()));
    }
  }

  @GetMapping("/list/{chatRoomId}")
  public ResponseEntity<?> listVoiceRooms(@PathVariable UUID chatRoomId) {
    try {
      ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
              .orElseThrow(() -> new RuntimeException("ChatRoom not found: " + chatRoomId));

      List<VoiceRoom> rooms = voiceRoomService.getVoiceRoomsForChatRoom(chatRoom);

      return ResponseEntity.ok(Map.of(
              "count", rooms.size(),
              "voiceRooms", rooms
      ));
    }
    catch (Exception e) {
      return ResponseEntity.status(500)
              .body(Map.of("error", "Failed to list voice rooms", "message", e.getMessage()));
    }
  }

  @DeleteMapping("/delete")
  public ResponseEntity<?> deleteVoiceRoom(
          @RequestParam UUID chatRoomId,
          @RequestParam String roomName,
          @RequestParam String requester) {
    try {
      ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
              .orElseThrow(() -> new RuntimeException("ChatRoom not found with ID: " + chatRoomId));

      voiceRoomService.deleteVoiceRoom(chatRoom, roomName, requester);

      return ResponseEntity.ok(Map.of("message", "Voice room deleted successfully"));
    }
    catch (Exception e) {
      logger.error("Error deleting voice room: {}", e.getMessage());
      return ResponseEntity.status(500)
              .body(Map.of("error", "Failed to delete voice room", "message", e.getMessage()));
    }
  }

}
