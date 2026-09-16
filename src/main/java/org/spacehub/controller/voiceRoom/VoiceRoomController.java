package org.spacehub.controller.voiceRoom;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.VoiceRoom.VoiceRoomDTO;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.VoiceRoom.VoiceRoom;
import org.spacehub.repository.ChatRoom.ChatRoomRepository;
import org.spacehub.service.Interface.IVoiceRoomService;
import org.spacehub.service.VoiceRoom.LiveKitTokenService;
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

  private final LiveKitTokenService liveKitTokenService;
  private final IVoiceRoomService voiceRoomService;
  private final ChatRoomRepository chatRoomRepository;

  @PostMapping("/token")
  public ResponseEntity<?> getLiveKitToken(
    @RequestParam String roomCode,
    @RequestParam(required = false) String identity,
    @RequestParam(required = false) String displayName) {

    try {
      String userEmail = identity != null && !identity.isBlank()
        ? identity
        : org.spacehub.utils.SecurityUtils.getCurrentUserEmail();

      String token = liveKitTokenService.createToken(roomCode, userEmail, displayName != null ? displayName : userEmail);

      return ResponseEntity.ok(Map.of(
        "token", token,
        "serverUrl", liveKitTokenService.getLivekitUrl(),
        "roomCode", roomCode,
        "identity", userEmail,
        "displayName", displayName != null ? displayName : userEmail
      ));
    } catch (Exception e) {
      logger.error("Error generating LiveKit token: {}", e.getMessage(), e);
      return ResponseEntity.status(500)
        .body(Map.of("error", "Failed to generate LiveKit token", "message", e.getMessage()));
    }
  }

  @GetMapping("/token")
  public ResponseEntity<?> getLiveKitTokenGet(
    @RequestParam String roomCode,
    @RequestParam(required = false) String identity,
    @RequestParam(required = false) String displayName) {
    return getLiveKitToken(roomCode, identity, displayName);
  }

  @PostMapping("/join")
  public ResponseEntity<?> joinVoiceRoom(
    @RequestParam(required = false) String roomCode,
    @RequestParam(required = false, defaultValue = "0") int janusRoomId,
    @RequestParam(required = false) String displayName) {

    try {
      String roomName = (roomCode != null && !roomCode.isBlank()) ? roomCode : "room_" + janusRoomId;
      String userEmail = org.spacehub.utils.SecurityUtils.getCurrentUserEmail();
      if (userEmail == null || userEmail.isBlank()) {
        userEmail = displayName != null ? displayName : "guest";
      }

      String token = liveKitTokenService.createToken(roomName, userEmail, displayName != null ? displayName : userEmail);

      return ResponseEntity.ok(Map.of(
        "message", "Joined voice room successfully",
        "token", token,
        "serverUrl", liveKitTokenService.getLivekitUrl(),
        "roomCode", roomName,
        "janusRoomId", janusRoomId,
        "identity", userEmail
      ));
    } catch (Exception e) {
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
    @RequestParam String roomName) {
    try {
      ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
        .orElseThrow(() -> new RuntimeException("ChatRoom not found with ID: " + chatRoomId));

      voiceRoomService.deleteVoiceRoom(chatRoom, roomName);

      return ResponseEntity.ok(Map.of("message", "Voice room deleted successfully"));
    }
    catch (Exception e) {
      logger.error("Error deleting voice room: {}", e.getMessage());
      return ResponseEntity.status(500)
        .body(Map.of("error", "Failed to delete voice room", "message", e.getMessage()));
    }
  }

}
