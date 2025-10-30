package org.spacehub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.spacehub.service.JanusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;
import java.util.Random;

@RestController
@RequestMapping("/api/voice-room")
@RequiredArgsConstructor
public class VoiceRoomController {

  private final JanusService janusService;

  @PostMapping("/create")
  public ResponseEntity<?> createRoom(@RequestParam String displayName) {
    String sessionId = janusService.createSession();
    String handleId = janusService.attachAudioBridgePlugin(sessionId);
    int roomId = 1000 + new Random().nextInt(9000);
    janusService.createAudioRoom(sessionId, handleId, roomId);
    JsonNode joinEvent = janusService.joinAudioRoom(sessionId, handleId, roomId, displayName);

    return ResponseEntity.ok(Map.of(
            "message", "Room created and joined successfully",
            "sessionId", sessionId,
            "handleId", handleId,
            "roomId", roomId,
            "joinEvent", Objects.toString(joinEvent, "")
    ));
  }

  @PostMapping("/join")
  public ResponseEntity<?> joinRoom(@RequestParam int roomId,
                                    @RequestParam String displayName) {

    String sessionId = janusService.createSession();
    String handleId = janusService.attachAudioBridgePlugin(sessionId);
    JsonNode joinEvent = janusService.joinAudioRoom(sessionId, handleId, roomId, displayName);

    return ResponseEntity.ok(Map.of(
            "message", "Joined room successfully",
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

    JsonNode answer = janusService.sendOffer(sessionId, handleId, sdp);
    return ResponseEntity.ok(answer);
  }
}
