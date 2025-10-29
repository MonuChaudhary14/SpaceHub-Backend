package org.spacehub.controller;

import org.spacehub.DTO.AcceptRequest;
import org.spacehub.DTO.CancelJoinRequest;
import org.spacehub.DTO.Community.*;
import org.spacehub.DTO.RejectRequest;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.Community.Community;
import org.spacehub.repository.community.CommunityRepository;
import org.spacehub.service.community.CommunityService;
import org.spacehub.specifications.CommunitySpecification;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("api/v1/community")
public class CommunityController {

  private final CommunityService communityService;
  private final CommunityRepository communityRepository;

  public CommunityController(CommunityService communityService, CommunityRepository communityRepository) {
    this.communityService = communityService;
    this.communityRepository = communityRepository;
  }

  @PostMapping("/create")
  public ResponseEntity<ApiResponse<Map<String, Object>>> createCommunity(@RequestParam("name") String name, @RequestParam("description") String description,
          @RequestParam("createdByEmail") String createdByEmail, @RequestParam("imageFile") MultipartFile imageFile) {
    return communityService.createCommunity(name, description, createdByEmail, imageFile);
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

  @GetMapping("/search")
  public ResponseEntity<List<CommunitySearchResponseDTO>> searchCommunities(@RequestParam(required = false) String name) {

    List<CommunitySearchResponseDTO> response = communityService.searchCommunities(name);
    return ResponseEntity.ok(response);
  }

}
