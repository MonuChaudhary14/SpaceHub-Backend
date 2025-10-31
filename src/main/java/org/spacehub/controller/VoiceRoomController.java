package org.spacehub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.spacehub.entities.VoiceRoom.VoiceRoom;
import org.spacehub.repository.VoiceRoomRepository;
import org.spacehub.service.JanusService;
import org.spacehub.service.VoiceRoomService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

@RestController
@RequestMapping("/api/voice-room")
@RequiredArgsConstructor
public class VoiceRoomController {

  private final JanusService janusService;
  private final VoiceRoomRepository voiceRoomRepository;
  private final VoiceRoomService voiceRoomService;

  @PostMapping("/create")
  public ResponseEntity<?> createRoom(@RequestParam String username) {
    Optional<VoiceRoom> existingRoom = voiceRoomRepository.findByRoomCode(username);
    if (existingRoom.isPresent() && existingRoom.get().isActive()) {
      return ResponseEntity.badRequest().body(Map.of(
              "message", "User already has an active voice room.",
              "roomCode", username
      ));
    }

    String sessionId = janusService.createSession();
    String handleId = janusService.attachAudioBridgePlugin(sessionId);
    int roomId = Math.abs(username.hashCode() % 90000) + 10000;

    janusService.createAudioRoom(sessionId, handleId, roomId);
    JsonNode joinEvent = janusService.joinAudioRoom(sessionId, handleId, roomId, username);

    VoiceRoom newRoom = VoiceRoom.builder()
            .roomCode(username).name("Voice Room of " + username).active(true).build();

    voiceRoomRepository.save(newRoom);

    return ResponseEntity.ok(Map.of(
            "message", "Voice room created successfully",
            "username", username,
            "sessionId", sessionId,
            "handleId", handleId,
            "roomId", roomId,
            "joinEvent", Objects.toString(joinEvent, "")
    ));
  }

  @PostMapping("/join")
  public ResponseEntity<?> joinRoom(@RequestParam String username, @RequestParam String displayName) {

    Optional<VoiceRoom> existingRoom = voiceRoomRepository.findByRoomCode(username);

    if (existingRoom.isEmpty() || !existingRoom.get().isActive()) {
      return ResponseEntity.badRequest().body(Map.of("message", "Voice room not found for user: " + username));
    }

    String sessionId = janusService.createSession();
    String handleId = janusService.attachAudioBridgePlugin(sessionId);
    int roomId = Math.abs(username.hashCode() % 90000) + 10000;

    JsonNode joinEvent = janusService.joinAudioRoom(sessionId, handleId, roomId, displayName);

    return ResponseEntity.ok(Map.of(
            "message", "Joined room successfully",
            "username", username,
            "roomId", roomId,
            "sessionId", sessionId,
            "handleId", handleId,
            "joinEvent", Objects.toString(joinEvent, "")
    ));
  }

  @PostMapping("/send-offer")
  public ResponseEntity<?> sendOffer(@RequestBody Map<String, String> body) {
    String sessionId = body.get("sessionId");
    String handleId = body.get("handleId");
    String sdp = body.get("sdp");

    if (sessionId == null || handleId == null || sdp == null)
      return ResponseEntity.badRequest().body(Map.of("message", "Missing sessionId, handleId, or sdp"));

    return ResponseEntity.ok(janusService.sendOffer(sessionId, handleId, sdp));
  }

  @DeleteMapping("/close")
  public ResponseEntity<?> closeRoom(@RequestParam String username) {
    Optional<VoiceRoom> existingRoom = voiceRoomRepository.findByRoomCode(username);
    if (existingRoom.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("message", "No voice room found for user: " + username));
    }

    VoiceRoom room = existingRoom.get();
    room.setActive(false);
    voiceRoomRepository.save(room);

    return ResponseEntity.ok(Map.of(
            "message", "Voice room closed successfully",
            "username", username
    ));
  }

  @PostMapping("/mute")
  public ResponseEntity<?> updateMuteState(@RequestParam String roomId, @RequestParam String username, @RequestParam boolean muted) {

    voiceRoomService.updateUserMuteState(roomId, username, muted);
    return ResponseEntity.ok(Map.of(
            "message", "Mute state updated successfully",
            "roomId", roomId,
            "username", username,
            "muted", muted
    ));
  }

  @GetMapping("/participants/{roomId}")
  public ResponseEntity<?> getParticipants(@PathVariable String roomId) {
    return ResponseEntity.ok(Map.of(
            "roomId", roomId,
            "participants", voiceRoomService.getParticipants(roomId)
    ));
  }

  @GetMapping("/mute-states/{roomId}")
  public ResponseEntity<?> getMuteStates(@PathVariable String roomId) {
    return ResponseEntity.ok(Map.of(
            "roomId", roomId,
            "muteStates", voiceRoomService.getMuteStates(roomId)
    ));
  }

}