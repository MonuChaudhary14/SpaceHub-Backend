package org.spacehub.controller;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.VoiceRoom.VoiceRoom;
import org.spacehub.repository.ChatRoom.ChatRoomRepository;
import org.spacehub.service.JanusService;
import org.spacehub.service.VoiceRoomService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/voice-room")
@RequiredArgsConstructor
public class VoiceRoomController {

  private static final Logger logger = LoggerFactory.getLogger(VoiceRoomController.class);
  private final JanusService janusService;
  private final VoiceRoomService voiceRoomService;
  private final ChatRoomRepository chatRoomRepository;

  @PostMapping("/create")
  public ResponseEntity<?> createVoiceRoom(
          @RequestParam String chatRoomCode,
          @RequestParam String name,
          @RequestParam String createdBy
  ) {
    try {
      ChatRoom chatRoom = chatRoomRepository.findByRoomCode(UUID.fromString(chatRoomCode))
              .orElseThrow(() -> new RuntimeException("Chat room not found"));

      VoiceRoom room = voiceRoomService.createVoiceRoom(chatRoom, name, createdBy);

      Map<String, Object> response = Map.of(
              "message", "Voice room created successfully",
              "voiceRoomName", room.getName(),
              "voiceRoomId", room.getId(),
              "janusRoomId", room.getJanusRoomId(),
              "chatRoom", chatRoom.getName()
      );

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      logger.error("Error creating voice room: {}", e.getMessage(), e);
      return ResponseEntity.status(500).body(Map.of(
              "error", "Failed to create voice room",
              "message", e.getMessage()
      ));
    }
  }

  @PostMapping("/join")
  public ResponseEntity<?> joinVoiceRoom(
          @RequestParam Integer voiceRoomId,
          @RequestParam String displayName
  ) {
    try {
      VoiceRoom voiceRoom = voiceRoomService.getVoiceRoomByJanusId(voiceRoomId);
      String sessionId = janusService.createSession();
      String handleId = janusService.attachAudioBridgePlugin(sessionId);

      janusService.joinAudioRoom(sessionId, handleId, voiceRoom.getJanusRoomId(), displayName);

      Map<String, Object> response = Map.of(
              "message", "Joined voice room successfully",
              "voiceRoomName", voiceRoom.getName(),
              "roomId", voiceRoom.getJanusRoomId(),
              "sessionId", sessionId,
              "handleId", handleId
      );

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      logger.error("Error joining voice room: {}", e.getMessage(), e);
      return ResponseEntity.status(500).body(Map.of(
              "error", "Failed to join voice room",
              "message", e.getMessage()
      ));
    }
  }

  @GetMapping("/list/{chatRoomCode}")
  public ResponseEntity<?> listVoiceRooms(@PathVariable String chatRoomCode) {
    try {
      ChatRoom chatRoom = chatRoomRepository.findByRoomCode(UUID.fromString(chatRoomCode))
              .orElseThrow(() -> new RuntimeException("Chat room not found"));

      List<VoiceRoom> voiceRooms = voiceRoomService.getVoiceRoomsForChatRoom(chatRoom);

      return ResponseEntity.ok(Map.of(
              "chatRoom", chatRoom.getName(),
              "totalVoiceRooms", voiceRooms.size(),
              "voiceRooms", voiceRooms
      ));
    } catch (Exception e) {
      logger.error("Error listing voice rooms: {}", e.getMessage(), e);
      return ResponseEntity.status(500).body(Map.of(
              "error", "Failed to list voice rooms",
              "message", e.getMessage()
      ));
    }
  }

  @DeleteMapping("/delete")
  public ResponseEntity<?> deleteVoiceRoom(
          @RequestParam String chatRoomCode,
          @RequestParam String name,
          @RequestParam String requester
  ) {
    try {
      ChatRoom chatRoom = chatRoomRepository.findByRoomCode(UUID.fromString(chatRoomCode))
              .orElseThrow(() -> new RuntimeException("Chat room not found"));

      voiceRoomService.deleteVoiceRoom(chatRoom, name, requester);
      return ResponseEntity.ok(Map.of("message", "Voice room deleted successfully"));
    } catch (Exception e) {
      logger.error("Error deleting voice room: {}", e.getMessage(), e);
      return ResponseEntity.status(500).body(Map.of(
              "error", "Failed to delete voice room",
              "message", e.getMessage()
      ));
    }
  }
}
