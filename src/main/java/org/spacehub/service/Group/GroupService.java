package org.spacehub.service.Group;

import org.spacehub.DTO.Group.CreateGroupRequest;
import org.spacehub.DTO.RenameGroupRequest;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface GroupService {

    ResponseEntity<ApiResponse<List<Map<String, Object>>>> getGroupsByCommunity(UUID communityId);

    ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityWithGroups(UUID communityId);

    ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityDetailsWithAdminFlag(UUID communityId, String requesterEmail);

    ResponseEntity<?> createGroupInCommunity(CreateGroupRequest request);

    ResponseEntity<?> renameGroupInCommunity(UUID communityId, UUID groupId, RenameGroupRequest request);

    ResponseEntity<?> deleteGroup(UUID communityId, UUID groupId, String requesterEmail);
}
