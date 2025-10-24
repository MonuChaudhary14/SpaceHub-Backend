package org.spacehub.controller;

import org.spacehub.DTO.*;
import org.spacehub.DTO.Community.*;
import org.spacehub.repository.commnunity.CommunityRepository;
import org.spacehub.service.S3Service;
import org.spacehub.service.community.CommunityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("api/v1/community")
public class CommunityController {

    @Autowired
    private CommunityService communityService;

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

    @PostMapping("/uploadImage")
    public ResponseEntity<?> uploadCommunityImage(@RequestParam Long communityId, @RequestParam("file") MultipartFile file) {
        return communityService.uploadCommunityImage(communityId, file);
    }

}
