package org.spacehub.controller;

import org.spacehub.DTO.AcceptRequest;
import org.spacehub.DTO.CancelJoinRequest;
import org.spacehub.DTO.Community.CommunityBlockRequest;
import org.spacehub.DTO.Community.CommunityChangeRoleRequest;
import org.spacehub.DTO.Community.CommunityMemberListRequest;
import org.spacehub.DTO.Community.CommunityMemberRequest;
import org.spacehub.DTO.Community.CommunityRoomsRequest;
import org.spacehub.DTO.Community.DeleteCommunityDTO;
import org.spacehub.DTO.Community.JoinCommunity;
import org.spacehub.DTO.Community.LeaveCommunity;
import org.spacehub.DTO.Community.RenameRoomRequest;
import org.spacehub.DTO.Community.UpdateCommunityDTO;
import org.spacehub.DTO.RejectRequest;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.community.CommunityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.spacehub.DTO.Community.CreateRoomRequest;
import java.util.Map;

@RestController
@RequestMapping("api/v1/community")
public class CommunityController {

  private final CommunityService communityService;

  public CommunityController(CommunityService communityService) {
    this.communityService = communityService;
  }

  @PostMapping("/create")
  public ResponseEntity<ApiResponse<Map<String, Object>>> createCommunity(
    @RequestParam("name") String name, @RequestParam("description") String description,
    @RequestParam("createdByEmail") String createdByEmail,
    @RequestParam("imageFile") MultipartFile imageFile) {
    return communityService.createCommunity(name, description, createdByEmail, imageFile);
  }

  @PostMapping("/delete")
  public ResponseEntity<?> deleteCommunity(@RequestBody DeleteCommunityDTO deleteCommunity) {
    return communityService.deleteCommunityByName(deleteCommunity);
  }

  @PostMapping("/requestJoin")
  public ResponseEntity<?> requestJoin(@RequestBody JoinCommunity joinCommunity) {
    return communityService.requestToJoinCommunity(joinCommunity);
  }

  @PostMapping("/cancelRequest")
  public ResponseEntity<?> cancelJoinRequest(@RequestBody CancelJoinRequest cancelJoinRequest) {
    return communityService.cancelRequestCommunity(cancelJoinRequest);
  }

  @PostMapping("/acceptRequest")
  public ResponseEntity<?> acceptRequest(@RequestBody AcceptRequest acceptRequest) {
    return communityService.acceptRequest(acceptRequest);
  }

  @PostMapping("/leave")
  public ResponseEntity<?> leaveCommunity(@RequestBody LeaveCommunity leaveCommunity) {
    return communityService.leaveCommunity(leaveCommunity);
  }

  @PostMapping("/rejectRequest")
  public ResponseEntity<?> rejectRequest(@RequestBody RejectRequest rejectRequest) {
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

  @GetMapping("/all")
  public ResponseEntity<?> listAllCommunities() {
    return communityService.listAllCommunities();
  }

  @GetMapping("/user/all/community")
  public ResponseEntity<?> listAllUserCommunities(@RequestBody String emailId) {
    return communityService.getUserCommunities(emailId);
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> getCommunityDetails(@PathVariable("id") Long communityId,
                                               @RequestParam("requesterEmail") String requesterEmail) {
    return communityService.getCommunityDetailsWithAdminFlag(communityId, requesterEmail);
  }

  @PostMapping("/rooms/create")
  public ResponseEntity<?> createRoomInCommunity(@RequestBody CreateRoomRequest request) {
    request.setCommunityId(request.getCommunityId());
    return communityService.createRoomInCommunity(request);
  }

  @GetMapping("/{id}/rooms/all")
  public ResponseEntity<?> getRoomsByCommunity(@PathVariable("id") Long communityId) {
    return communityService.getRoomsByCommunity(communityId);
  }

  @DeleteMapping("/rooms/{roomId}")
  public ResponseEntity<?> deleteRoom(@PathVariable("roomId") Long roomId,
                                      @RequestParam("requesterEmail") String requesterEmail) {
    return communityService.deleteRoom(roomId, requesterEmail);
  }

  @GetMapping("/search")
  public ResponseEntity<?> searchCommunities(
    @RequestParam("q") String q,
    @RequestParam(value = "requesterEmail", required = false) String requesterEmail,
    @RequestParam(value = "page", defaultValue = "0") int page,
    @RequestParam(value = "size", defaultValue = "20") int size
  ) {
    return communityService.searchCommunities(q, requesterEmail, page, size);
  }

  @PostMapping("/{id}/enter")
  public ResponseEntity<?> enterCommunity(
    @PathVariable("id") Long communityId,
    @RequestParam("requesterEmail") String requesterEmail
  ) {
    return communityService.enterOrRequestCommunity(communityId, requesterEmail);
  }

  @PostMapping("/{id}/upload-avatar")
  public ResponseEntity<?> uploadCommunityAvatar(
    @PathVariable("id") Long communityId,
    @RequestParam("requesterEmail") String requesterEmail,
    @RequestParam("imageFile") MultipartFile imageFile) {
    return communityService.uploadCommunityAvatar(communityId, requesterEmail, imageFile);
  }

  @PostMapping("/{id}/upload-banner")
  public ResponseEntity<?> uploadCommunityBanner(
    @PathVariable("id") Long communityId,
    @RequestParam("requesterEmail") String requesterEmail,
    @RequestParam(value = "imageFile", required = false) MultipartFile bannerFile,
    @RequestParam(value = "avatarFile", required = false) MultipartFile communityAvatarFile,
    @RequestParam(value = "userAvatarFile", required = false) MultipartFile userAvatarFile,
    @RequestParam(value = "name", required = false) String name,
    @RequestParam(value = "description", required = false) String description
  ) {
    return communityService.uploadCommunityBanner(
      communityId, requesterEmail, bannerFile, communityAvatarFile, userAvatarFile, name, description
    );
  }

  @PutMapping("/{communityId}/rooms/{roomId}/rename")
  public ResponseEntity<?> renameRoom(
    @PathVariable Long communityId,
    @PathVariable Long roomId,
    @RequestBody RenameRoomRequest req) {
    req.setRequesterEmail(req.getRequesterEmail());
    return communityService.renameRoomInCommunity(communityId, roomId, req);
  }

  @GetMapping("/{id}/roles")
  public ResponseEntity<?> getRoles(@PathVariable("id") Long communityId,
                                    @RequestParam("requesterEmail") String requesterEmail) {
    return communityService.getRolesForRequester(communityId, requesterEmail);
  }

  @GetMapping("/discover")
  public ResponseEntity<?> discoverCommunities(
    @RequestParam(value = "page", defaultValue = "0") int page,
    @RequestParam(value = "size", defaultValue = "20") int size) {
    return communityService.discoverCommunities(page, size);
  }

}
