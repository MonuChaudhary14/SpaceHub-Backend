package org.spacehub.controller;

import org.spacehub.service.JanusVideoService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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


}
