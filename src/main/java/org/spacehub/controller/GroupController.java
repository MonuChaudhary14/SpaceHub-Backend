package org.spacehub.controller;

import lombok.RequiredArgsConstructor;
import org.spacehub.dto.CreateGroupRequest;
import org.spacehub.dto.RenameGroupRequest;
import org.spacehub.response.ApiResponse;
import org.spacehub.service.GroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @GetMapping("/community/{communityId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getGroupsByCommunity(
            @PathVariable UUID communityId) {
        return groupService.getGroupsByCommunity(communityId);
    }

    @GetMapping("/community/{communityId}/details")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityWithGroups(
            @PathVariable UUID communityId) {
        return groupService.getCommunityWithGroups(communityId);
    }

    @GetMapping("/community/{communityId}/details/{email}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityDetailsWithAdminFlag(
            @PathVariable UUID communityId,
            @PathVariable("email") String requesterEmail) {
        return groupService.getCommunityDetailsWithAdminFlag(communityId, requesterEmail);
    }

    @PostMapping("/create")
    public ResponseEntity<?> createGroupInCommunity(@RequestBody CreateGroupRequest request) {
        return groupService.createGroupInCommunity(request);
    }

    @PatchMapping("/{communityId}/{groupId}/rename")
    public ResponseEntity<?> renameGroupInCommunity(
            @PathVariable UUID communityId,
            @PathVariable UUID groupId,
            @RequestBody RenameGroupRequest request) {
        return groupService.renameGroupInCommunity(communityId, groupId, request);
    }

    @DeleteMapping("/{communityId}/{groupId}")
    public ResponseEntity<?> deleteGroup(
            @PathVariable UUID communityId,
            @PathVariable UUID groupId,
            @RequestParam String requesterEmail) {
        return groupService.deleteGroup(communityId, groupId, requesterEmail);
    }
}
