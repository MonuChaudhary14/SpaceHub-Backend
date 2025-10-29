package org.spacehub.controller;

import org.spacehub.DTO.AcceptRequest;
import org.spacehub.DTO.CancelJoinRequest;
import org.spacehub.DTO.Community.CommunityBlockRequest;
import org.spacehub.DTO.Community.CommunityChangeRoleRequest;
import org.spacehub.DTO.Community.CommunityDTO;
import org.spacehub.DTO.Community.CommunityMemberListRequest;
import org.spacehub.DTO.Community.CommunityMemberRequest;
import org.spacehub.DTO.Community.CommunityRoomsRequest;
import org.spacehub.DTO.Community.DeleteCommunityDTO;
import org.spacehub.DTO.Community.JoinCommunity;
import org.spacehub.DTO.Community.LeaveCommunity;
import org.spacehub.DTO.Community.UpdateCommunityDTO;
import org.spacehub.DTO.RejectRequest;
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
    return communityService.createCommunity(community);
  }

  @PostMapping("/delete")
  public ResponseEntity<?> deleteCommunity(@RequestBody DeleteCommunityDTO deleteCommunity) {
    return communityService.deleteCommunityByName(deleteCommunity);
  }

  @PostMapping("/requestJoin")
  public ResponseEntity<?> requestJoin(@RequestBody JoinCommunity joinCommunity){
    return communityService.requestToJoinCommunity(joinCommunity);
  }

  @PostMapping("/cancelRequest")
  public ResponseEntity<?> cancelJoinRequest(@RequestBody CancelJoinRequest cancelJoinRequest){
    return communityService.cancelRequestCommunity(cancelJoinRequest);
  }

  @PostMapping("/acceptRequest")
  public ResponseEntity<?> acceptRequest(@RequestBody AcceptRequest acceptRequest){
    return communityService.acceptRequest(acceptRequest);
  }

  @PostMapping("/leave")
  public ResponseEntity<?> leaveCommunity(@RequestBody LeaveCommunity leaveCommunity) {
    return communityService.leaveCommunity(leaveCommunity);
  }

  @PostMapping("/rejectRequest")
  public ResponseEntity<?> rejectRequest(@RequestBody RejectRequest rejectRequest){
    return communityService.rejectRequest(rejectRequest);
  }

  @PostMapping("/getCommunityRooms")
  public ResponseEntity<?> getCommunityRooms(@RequestBody CommunityRoomsRequest request) {
    if (request.getCommunityId() == null) {
      return ResponseEntity.badRequest().body("communityId is required");
    }
    return communityService.getCommunityWithRooms(request.getCommunityId());
  }

  @PostMapping("/removeMember")
  public ResponseEntity<?> removeMember(@RequestBody CommunityMemberRequest request) {
    return communityService.removeMemberFromCommunity(request);
  }

  @PostMapping("/changeRole")
  public ResponseEntity<?> changeRole(@RequestBody CommunityChangeRoleRequest request) {
    return communityService.changeMemberRole(request);
  }

  @PostMapping("/members")
  public ResponseEntity<?> getCommunityMembers(@RequestBody CommunityMemberListRequest request) {
    return communityService.getCommunityMembers(request.getCommunityId());
  }

  @PostMapping("/blockMember")
  public ResponseEntity<?> blockOrUnblockMember(@RequestBody CommunityBlockRequest request) {
    return communityService.blockOrUnblockMember(request);
  }

  @PostMapping("/updateInfo")
  public ResponseEntity<?> updateCommunityInfo(@RequestBody UpdateCommunityDTO dto) {
    return communityService.updateCommunityInfo(dto);
  }

}
