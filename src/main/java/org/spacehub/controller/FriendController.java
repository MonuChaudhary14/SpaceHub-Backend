package org.spacehub.controller;

import org.spacehub.DTO.*;
import org.spacehub.DTO.User.UserEmail;
import org.spacehub.DTO.User.UserOutput;
import org.spacehub.service.Interface.IFriendService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/friends")
public class FriendController {

  private final IFriendService friendService;

  public FriendController(IFriendService friendService) {
    this.friendService = friendService;
  }

  @PostMapping("/request")
  public ResponseEntity<String> sendFriendRequest(@RequestBody FriendRequest request) {
    try {
      String response = friendService.sendFriendRequest(request.getUserEmail(), request.getFriendEmail());
      return ResponseEntity.ok(response);
    }
    catch (Exception e) {
      return ResponseEntity.badRequest().body("Error: " + e.getMessage());
    }
  }

  @PostMapping("/respond")
  public ResponseEntity<String> respondFriendRequest(@RequestBody RespondFriendRequest request) {
    try {
      String response = friendService.respondFriendRequest(request.getUserEmail(), request.getRequesterEmail(), request.isAccept());
      return ResponseEntity.ok(response);
    }
    catch (Exception e) {
      return ResponseEntity.badRequest().body("Error: " + e.getMessage());
    }
  }

  @PostMapping("/list")
  public ResponseEntity<?> getFriends(@RequestBody UserEmail request) {
    try {
      List<UserOutput> friends = friendService.getFriends(request.getUserEmail());
      return ResponseEntity.ok(friends);
    }
    catch (Exception e) {
      return ResponseEntity.badRequest().body("Error: " + e.getMessage());
    }
  }

  @PostMapping("/block")
  public ResponseEntity<String> blockFriend(@RequestBody BlockUnblock request) {
    try {
      String response = friendService.blockFriend(request.getUserEmail(), request.getFriendEmail());
      return ResponseEntity.ok(response);
    }
    catch (Exception e) {
      return ResponseEntity.badRequest().body("Error: " + e.getMessage());
    }
  }

  @PostMapping("/unblock")
  public ResponseEntity<String> unblockUser(@RequestBody BlockUnblock request) {
    try {
      String response = friendService.unblockUser(request.getUserEmail(), request.getFriendEmail());
      return ResponseEntity.ok(response);
    }
    catch (Exception e) {
      return ResponseEntity.badRequest().body("Error: " + e.getMessage());
    }
  }

  @PostMapping("/pending/incoming")
  public ResponseEntity<?> getIncomingRequests(@RequestBody UserEmail request) {
    try {
      List<UserOutput> incoming = friendService.getIncomingPendingRequests(request.getUserEmail());
      return ResponseEntity.ok(incoming);
    }
    catch (Exception e) {
      return ResponseEntity.badRequest().body("Error: " + e.getMessage());
    }
  }

  @PostMapping("/pending/outgoing")
  public ResponseEntity<?> getOutgoingRequests(@RequestBody UserEmail request) {
    try {
      List<UserOutput> outgoing = friendService.getOutgoingPendingRequests(request.getUserEmail());
      return ResponseEntity.ok(outgoing);
    }
    catch (Exception e) {
      return ResponseEntity.badRequest().body("Error: " + e.getMessage());
    }
  }
}
