package org.spacehub.controller;

import lombok.RequiredArgsConstructor;
import org.spacehub.service.JanusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/voice-room")
@RequiredArgsConstructor
public class VoiceRoomController {

  private final JanusService janusService;
  private static final Logger logger = LoggerFactory.getLogger(VoiceRoomController.class);

  @PostMapping("/create")
  public ResponseEntity<?> createRoom(@RequestParam String displayName) {
    try {
      String sessionId = janusService.createSession();
      String handleId = janusService.attachAudioBridgePlugin(sessionId);
      int roomId = 1000 + new Random().nextInt(9000);

      janusService.createAudioRoom(sessionId, handleId, roomId);
      janusService.joinAudioRoom(sessionId, handleId, roomId, displayName);

      return ResponseEntity.ok(Map.of(
        "message", "Room creation requested",
        "sessionId", sessionId,
        "handleId", handleId,
        "roomId", roomId
      ));
    } catch (Exception e) {
      logger.error("Error creating room: {}", e.getMessage(), e);
      return ResponseEntity.status(500).body(Map.of("error", "Failed to create room", "message",
        e.getMessage()));
    }
  }

  @PostMapping("/join")
  public ResponseEntity<?> joinRoom(@RequestParam int roomId,
                                    @RequestParam String displayName) {
    try {
      String sessionId = janusService.createSession();
      String handleId = janusService.attachAudioBridgePlugin(sessionId);

      janusService.joinAudioRoom(sessionId, handleId, roomId, displayName);

      return ResponseEntity.ok(Map.of(
        "message", "Join room requested",
        "roomId", roomId,
        "sessionId", sessionId,
        "handleId", handleId
      ));
    } catch (Exception e) {
      logger.error("Error joining room: {}", e.getMessage(), e);
      return ResponseEntity.status(500).body(Map.of("error", "Failed to join room", "message",
        e.getMessage()));
    }
  }
}
