package org.spacehub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.spacehub.service.JanusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/voice-room")
@RequiredArgsConstructor
public class VoiceRoomController {

  private final JanusService janusService;

  @PostMapping("/create")
  public ResponseEntity<?> createRoom() {
    String sessionId = janusService.createSession();
    String handleId = janusService.attachAudioBridgePlugin(sessionId);
    int roomId = 1000 + new Random().nextInt(9000);
    janusService.createAudioRoom(sessionId, handleId, roomId);

    return ResponseEntity.ok(Map.of(
      "message", "Room created successfully",
      "sessionId", sessionId,
      "handleId", handleId,
      "roomId", roomId
    ));
  }

  @PostMapping("/join")
  public ResponseEntity<?> joinRoom(@RequestParam String sessionId,
                                    @RequestParam String handleId,
                                    @RequestParam int roomId,
                                    @RequestParam String displayName) {
    String token = janusService.joinAudioRoom(sessionId, handleId, roomId, displayName);
    return ResponseEntity.ok(Map.of(
      "message", "Joined room successfully",
      "roomId", roomId,
      "sessionId", sessionId,
      "handleId", handleId,
      "status", token
    ));
  }

  @PostMapping("/leave")
  public ResponseEntity<?> leaveRoom(@RequestParam String sessionId,
                                     @RequestParam String handleId) {
    janusService.leaveRoom(sessionId, handleId);
    return ResponseEntity.ok(Map.of("message", "Left the room successfully"));
  }

  @PostMapping("/send-offer")
  public ResponseEntity<?> sendOffer(@RequestBody Map<String, String> body) {
    String sessionId = body.get("sessionId");
    String handleId = body.get("handleId");
    String sdp = body.get("sdp");

    JsonNode janusResponse = janusService.sendOffer(sessionId, handleId, sdp);
    return ResponseEntity.ok(janusResponse);
  }
}
