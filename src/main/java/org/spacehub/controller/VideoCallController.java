package org.spacehub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.spacehub.service.JanusVideoService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/video")
public class VideoCallController {

  private final JanusVideoService janusService;

  public VideoCallController(JanusVideoService janusService) {
    this.janusService = janusService;
  }

  @PostMapping("/createSession")
  public String createSession() {
    return janusService.createSession();
  }

  @PostMapping("/createRoom/{roomId}")
  public String createRoom(@PathVariable int roomId) {

    String sessionId = janusService.createSession();
    String handleId = janusService.attachVideoRoomPlugin(sessionId);

    janusService.createVideoRoom(sessionId, handleId, roomId, "SpaceHub Video Room");
    return "Room " + roomId + " created with session " + sessionId;
  }

  @PostMapping("/attachPlugin/{sessionId}")
  public String attachPlugin(@PathVariable String sessionId) {
    return janusService.attachVideoRoomPlugin(sessionId);
  }

  @PostMapping("/publish/{sessionId}/{handleId}")
  public JsonNode publishFeed(@PathVariable String sessionId, @PathVariable String handleId,
                              @RequestBody String sdpOffer) {
    return janusService.publishOwnFeed(sessionId, handleId, sdpOffer);
  }

  @PostMapping("/join/{sessionId}/{handleId}/{roomId}")
  public void joinRoom(@PathVariable String sessionId, @PathVariable String handleId,
                       @PathVariable int roomId, @RequestParam String displayName) {
    janusService.joinVideoRoom(sessionId, handleId, roomId, displayName);
  }

  @PostMapping("/subscribe/{sessionId}/{handleId}/{roomId}/{feedId}")
  public JsonNode subscribeFeed(@PathVariable String sessionId, @PathVariable String handleId,
                                @PathVariable int roomId, @PathVariable int feedId) {
    return janusService.subscribeToFeed(sessionId, handleId, roomId, feedId);
  }

  @PostMapping("/leave/{sessionId}/{handleId}")
  public void leaveRoom(@PathVariable String sessionId, @PathVariable String handleId) {
    janusService.leaveVideoRoom(sessionId, handleId);
  }
}
