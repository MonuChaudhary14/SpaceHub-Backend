package org.spacehub.controller;

import org.spacehub.DTO.*;
import org.spacehub.service.CommunityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/community")
public class CommunityController {

  private final CommunityService communityService;

  public CommunityController(CommunityService communityService) {
    this.communityService = communityService;
  }

  @PostMapping("/create")
  public ResponseEntity<?> createCommunity(@RequestBody CommunityDTO community) {
    return ResponseEntity.status(200).body(communityService.createCommunity(community));
  }

  @PostMapping("/delete")
  public ResponseEntity<?> deleteCommunity(@RequestBody DeleteCommunityDTO deleteCommunity) {
    return communityService.deleteCommunityByName(deleteCommunity);
  }

  @PostMapping("/requestJoin")
  public ResponseEntity<?> requestJoin(@RequestBody JoinCommunity joinCommunity){
    return ResponseEntity.status(200).body(communityService.requestToJoinCommunity(joinCommunity));
  }

  @PostMapping("/cancelRequest")
  public ResponseEntity<?> cancelJoinRequest(@RequestBody CancelJoinRequest cancelJoinRequest){
    return ResponseEntity.status(200).body(communityService.cancelRequestCommunity(cancelJoinRequest));
  }

  @PostMapping("/acceptRequest")
  public ResponseEntity<?> acceptRequest(@RequestBody AcceptRequest acceptRequest){
    return ResponseEntity.status(200).body(communityService.acceptRequest(acceptRequest));
  }

  @PostMapping("/leave")
  public ResponseEntity<?> leaveCommunity(@RequestBody LeaveCommunity leaveCommunity) {
    return ResponseEntity.status(200).body(communityService.leaveCommunity(leaveCommunity).getBody());
  }

  @PostMapping("/rejectRequest")
  public ResponseEntity<?> rejectRequest(@RequestBody RejectRequest rejectRequest){
    return ResponseEntity.status(200).body(communityService.rejectRequest(rejectRequest));
  }

}
