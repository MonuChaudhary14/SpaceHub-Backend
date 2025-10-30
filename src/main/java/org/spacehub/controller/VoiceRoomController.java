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

    JsonNode janusResponse = janusService.sendOffer(sessionId, handleId, sdp);
    return ResponseEntity.ok(janusResponse);
  }

  @PostMapping("/subscribe")
  public ResponseEntity<?> subscribeToFeed(@RequestBody Map<String, String> body) {
    String sessionId = body.get("sessionId");
    String roomIdStr = body.get("roomId");
    String feedIdStr = body.get("feedId");

    if (sessionId == null || roomIdStr == null || feedIdStr == null) {
      return ResponseEntity.badRequest().body(Map.of("error", "missing parameters"));
    }
    int roomId = Integer.parseInt(roomIdStr);
    int feedId = Integer.parseInt(feedIdStr);

    String listenerHandleId = janusService.attachAudioBridgePlugin(sessionId);

    Map<String, Object> joinBody = Map.of(
            "request", "join",
            "room", roomId,
            "ptype", "listener",
            "feed", feedId
    );

    janusService.sendMessage(sessionId, listenerHandleId, joinBody);

    JsonNode event = janusService.fetchSessionEvents(sessionId);

    return ResponseEntity.ok(Map.of(
            "listenerHandleId", listenerHandleId,
            "event", Objects.toString(event, "")
    ));
  }

  @PostMapping("/subscribe-answer")
  public ResponseEntity<?> sendSubscribeAnswer(@RequestBody Map<String, Object> body) {
    String sessionId = (String) body.get("sessionId");
    String handleId = (String) body.get("handleId");
    Object roomIdObj = body.get("roomId");
    Object jsepObj = body.get("jsep");

    if (sessionId == null || handleId == null || roomIdObj == null || jsepObj == null) {
      return ResponseEntity.badRequest().body(Map.of("error", "missing parameters"));
    }

    int roomId = Integer.parseInt(roomIdObj.toString());
    @SuppressWarnings("unchecked")
    Map<String, Object> jsep = (Map<String, Object>) jsepObj;

    Map<String, Object> startBody = Map.of("request", "start", "room", roomId);

    janusService.sendMessageWithJsep(sessionId, handleId, startBody, jsep);

    JsonNode evt = janusService.fetchSessionEvents(sessionId);
    return ResponseEntity.ok(Map.of("event", Objects.toString(evt, "")));
  }

  @PostMapping("/mute")
  public ResponseEntity<?> toggleMute(@RequestParam String sessionId,
                                      @RequestParam String handleId,
                                      @RequestParam boolean mute) {
    janusService.setMute(sessionId, handleId, mute);
    return ResponseEntity.ok(Map.of("muted", mute));
  }

}
