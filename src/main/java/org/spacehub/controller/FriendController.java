package org.spacehub.controller;

import org.spacehub.DTO.*;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.FriendService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/friends")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @PostMapping("/request")
    public ResponseEntity<ApiResponse<String>> sendFriendRequest(@RequestBody FriendRequest request) {
        try {
            String response = friendService.sendFriendRequest(request.getUserEmail(), request.getFriendEmail());
            return ResponseEntity.ok(new ApiResponse<>(200, response, null));
        }
        catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
        }
    }

    @PostMapping("/respond")
    public ResponseEntity<ApiResponse<String>> respondFriendRequest(@RequestBody RespondFriendRequest request) {
        try {
            String response = friendService.respondFriendRequest(request.getUserEmail(), request.getRequesterEmail(), request.isAccept());
            return ResponseEntity.ok(new ApiResponse<>(200, response, null));
        }
        catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
        }
    }

    @PostMapping("/list")
    public ResponseEntity<ApiResponse<List<UserOutput>>> getFriends(@RequestBody UserEmail request) {
        try {
            List<UserOutput> friends = friendService.getFriends(request.getUserEmail());
            return ResponseEntity.ok(new ApiResponse<>(200, "Friends list retrieved", friends));
        }
        catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
        }
    }

    @PostMapping("/block")
    public ResponseEntity<ApiResponse<String>> blockFriend(@RequestBody BlockUnblock request) {
        try {
            String response = friendService.blockFriend(request.getUserEmail(), request.getFriendEmail());
            return ResponseEntity.ok(new ApiResponse<>(200, response, null));
        }
        catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
        }
    }

    @PostMapping("/unblock")
    public ResponseEntity<ApiResponse<String>> unblockUser(@RequestBody BlockUnblock request) {
        try {
            String response = friendService.unblockUser(request.getUserEmail(), request.getFriendEmail());
            return ResponseEntity.ok(new ApiResponse<>(200, response, null));
        }
        catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
        }
    }

    @PostMapping("/pending/incoming")
    public ResponseEntity<ApiResponse<List<UserOutput>>> getIncomingRequests(@RequestBody UserEmail request) {
        try {
            List<UserOutput> incoming = friendService.getIncomingPendingRequests(request.getUserEmail());
            return ResponseEntity.ok(new ApiResponse<>(200, "Incoming friend requests", incoming));
        }
        catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
        }
    }

    @PostMapping("/pending/outgoing")
    public ResponseEntity<ApiResponse<List<UserOutput>>> getOutgoingRequests(@RequestBody UserEmail request) {
        try {
            List<UserOutput> outgoing = friendService.getOutgoingPendingRequests(request.getUserEmail());
            return ResponseEntity.ok(new ApiResponse<>(200, "Outgoing friend requests", outgoing));
        }
        catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
        }
    }
}
