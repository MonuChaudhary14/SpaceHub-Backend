package org.spacehub.controller.Friend;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.Friend.FriendRequest;
import org.spacehub.DTO.Friend.RespondFriendRequest;
import org.spacehub.DTO.Community.BlockUnblock;
import org.spacehub.DTO.User.UserEmail;
import org.spacehub.DTO.User.UserOutput;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.Interface.IFriendService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/friends")
@RequiredArgsConstructor
public class FriendController {

  private final IFriendService friendService;

  @PostMapping("/request")
  public ResponseEntity<ApiResponse<String>> sendFriendRequest(@RequestBody FriendRequest request) {
    try {
      String response = friendService.sendFriendRequest(request.getUserEmail(), request.getFriendEmail());
      return ResponseEntity.ok(new ApiResponse<>(200, response));
    }
    catch (Exception e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Error: " + e.getMessage()));
    }
  }

  @PostMapping("/respond")
  public ResponseEntity<ApiResponse<String>> respondFriendRequest(@RequestBody RespondFriendRequest request) {
    try {
      String response = friendService.respondFriendRequest(request.getUserEmail(), request.getRequesterEmail(), request.isAccept());
      return ResponseEntity.ok(new ApiResponse<>(200, response));
    }
    catch (Exception e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Error: " + e.getMessage()));
    }
  }

  @PostMapping("/list")
  public ResponseEntity<ApiResponse<List<UserOutput>>> getFriends(@RequestBody UserEmail request) {
    try {
      List<UserOutput> friends = friendService.getFriends(request.getUserEmail());
      return ResponseEntity.ok(new ApiResponse<>(200, "Friends list retrieved successfully", friends));
    }
    catch (Exception e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Error: " + e.getMessage()));
    }
  }

  @PostMapping("/block")
  public ResponseEntity<ApiResponse<String>> blockFriend(@RequestBody BlockUnblock request) {
    try {
      String response = friendService.blockFriend(request.getUserEmail(), request.getFriendEmail());
      return ResponseEntity.ok(new ApiResponse<>(200, response));
    }
    catch (Exception e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Error: " + e.getMessage()));
    }
  }

  @PostMapping("/unblock")
  public ResponseEntity<ApiResponse<String>> unblockUser(@RequestBody BlockUnblock request) {
    try {
      String response = friendService.unblockUser(request.getUserEmail(), request.getFriendEmail());
      return ResponseEntity.ok(new ApiResponse<>(200, response));
    }
    catch (Exception e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Error: " + e.getMessage()));
    }
  }


  @PostMapping("/pending/incoming")
  public ResponseEntity<ApiResponse<List<UserOutput>>> getIncomingRequests(@RequestBody UserEmail request) {
    try {
      List<UserOutput> incoming = friendService.getIncomingPendingRequests(request.getUserEmail());
      return ResponseEntity.ok(new ApiResponse<>(200, "Incoming requests fetched successfully", incoming));
    }
    catch (Exception e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Error: " + e.getMessage()));
    }
  }

  @PostMapping("/pending/outgoing")
  public ResponseEntity<ApiResponse<List<UserOutput>>> getOutgoingRequests(@RequestBody UserEmail request) {
    try {
      List<UserOutput> outgoing = friendService.getOutgoingPendingRequests(request.getUserEmail());
      return ResponseEntity.ok(new ApiResponse<>(200, "Outgoing requests fetched successfully", outgoing));
    }
    catch (Exception e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Error: " + e.getMessage()));
    }
  }

  @PostMapping("/remove")
  public ResponseEntity<ApiResponse<String>> removeFriend(@RequestBody BlockUnblock request) {
    try {
      String response = friendService.removeFriend(request.getUserEmail(), request.getFriendEmail());
      return ResponseEntity.ok(new ApiResponse<>(200, response));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Error: " + e.getMessage()));
    }
  }

}
