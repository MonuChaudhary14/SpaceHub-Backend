package org.spacehub.controller;

import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.ChatPoll;
import org.spacehub.service.ChatRoom.ChatPollService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat/polls")
public class PollController {

    private final ChatPollService chatPollService;

    public PollController(ChatPollService chatPollService) {
        this.chatPollService = chatPollService;
    }

    @GetMapping("/getpolls/{roomCode}")
    public ResponseEntity<ApiResponse<List<ChatPoll>>> getPolls(@PathVariable String roomCode) {
        try {
            List<ChatPoll> polls = chatPollService.getPollsForRoom(roomCode);
            ApiResponse<List<ChatPoll>> response = new ApiResponse<>(200, "Polls fetched successfully", polls);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            ApiResponse<List<ChatPoll>> response = new ApiResponse<>(400, e.getMessage(), null);
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/create/{roomCode}")
    public ResponseEntity<ApiResponse<ChatPoll>> createPoll(@PathVariable String roomCode, @RequestParam String userId, @RequestBody Map<String, Object> body) {
        try {
            ChatPoll poll = chatPollService.createPoll(roomCode, userId, body);
            ApiResponse<ChatPoll> response = new ApiResponse<>(200, "Poll created successfully", poll);
            return ResponseEntity.ok(response);
        }
        catch (RuntimeException e) {
            ApiResponse<ChatPoll> response = new ApiResponse<>(403, e.getMessage(), null);
            return ResponseEntity.status(403).body(response);
        }
    }

    @PostMapping("/{roomCode}/vote")
    public ResponseEntity<ApiResponse<String>> vote(@PathVariable String roomCode, @RequestParam String userId, @RequestBody Map<String, Object> body) {
        try {
            chatPollService.vote(roomCode, userId, body);
            ApiResponse<String> response = new ApiResponse<>(200, "Vote submitted successfully", null);
            return ResponseEntity.ok(response);
        }
        catch (RuntimeException e) {
            ApiResponse<String> response = new ApiResponse<>(400, e.getMessage(), null);
            return ResponseEntity.badRequest().body(response);
        }
    }
}