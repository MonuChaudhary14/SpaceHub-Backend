package org.spacehub.service.community.CommunityInterfaces;

import org.spacehub.DTO.Community.AcceptRequest;
import org.spacehub.DTO.Community.CancelJoinRequest;
import org.spacehub.DTO.Community.*;
import org.spacehub.DTO.Community.RejectRequest;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.Community.Community;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public interface ICommunityService {

  ResponseEntity<ApiResponse<Map<String, Object>>> createCommunity(
    String name,
    String description,
    MultipartFile imageFile
  );

  ResponseEntity<?> deleteCommunityByName(DeleteCommunityDTO deleteCommunity);

  ResponseEntity<?> requestToJoinCommunity(JoinCommunity joinCommunity);

  ResponseEntity<?> cancelRequestCommunity(CancelJoinRequest cancelJoinRequest);

  ResponseEntity<?> acceptRequest(AcceptRequest acceptRequest);

  ResponseEntity<?> leaveCommunity(LeaveCommunity leaveCommunity);

  ResponseEntity<?> rejectRequest(RejectRequest rejectRequest);

  ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityWithRooms(UUID communityId);

  ResponseEntity<ApiResponse<String>> removeMemberFromCommunity(CommunityMemberRequest request);

  ResponseEntity<ApiResponse<String>> changeMemberRole(CommunityChangeRoleRequest request);

  ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityMembers(UUID communityId);

  ResponseEntity<ApiResponse<String>> blockOrUnblockMember(CommunityBlockRequest request);

  ResponseEntity<ApiResponse<Community>> updateCommunityInfo(UpdateCommunityDTO dto);

  ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> listAllCommunities();

  ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityDetailsWithAdminFlag(
          UUID communityId
  );

  ResponseEntity<?> createRoomInCommunity(CreateRoomRequest request);

  ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRoomsByCommunity(UUID communityId);

  ResponseEntity<?> deleteRoom(UUID communityId, UUID roomId);

  ResponseEntity<?> searchCommunities(String q, int page, int size);

  ResponseEntity<?> enterOrRequestCommunity(UUID communityId);

  ResponseEntity<?> uploadCommunityAvatar(UUID communityId, MultipartFile imageFile);

  ResponseEntity<?> uploadCommunityBanner(
          UUID communityId,
          MultipartFile bannerFile,
          MultipartFile communityAvatarFile,
          MultipartFile userAvatarFile,
          String newName,
          String newDescription
  );

  ResponseEntity<?> renameRoomInCommunity(UUID communityId, UUID roomId, RenameRoomRequest req);

  ResponseEntity<?> getRolesForRequester(UUID communityId);

  ResponseEntity<ApiResponse<Map<String, Object>>> discoverCommunities(int page, int size);

  ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> listMyCommunities();

  ResponseEntity<ApiResponse<?>> getPendingRequests(UUID communityId);

  ResponseEntity<ApiResponse<?>> getAllPendingRequestsForAdmin();

  ResponseEntity<?> checkCommunityNameExists(String name);

}

