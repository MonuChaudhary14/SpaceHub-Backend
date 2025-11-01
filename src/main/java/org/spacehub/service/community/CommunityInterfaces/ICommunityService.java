package org.spacehub.service.community.CommunityInterfaces;

import org.spacehub.DTO.AcceptRequest;
import org.spacehub.DTO.CancelJoinRequest;
import org.spacehub.DTO.Community.*;
import org.spacehub.DTO.RejectRequest;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.Community.Community;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

public interface ICommunityService {

  ResponseEntity<ApiResponse<Map<String, Object>>> createCommunity(
    String name,
    String description,
    String createdByEmail,
    MultipartFile imageFile
  );

  ResponseEntity<?> deleteCommunityByName(DeleteCommunityDTO deleteCommunity);

  ResponseEntity<?> requestToJoinCommunity(JoinCommunity joinCommunity);

  ResponseEntity<?> cancelRequestCommunity(CancelJoinRequest cancelJoinRequest);

  ResponseEntity<?> acceptRequest(AcceptRequest acceptRequest);

  ResponseEntity<?> leaveCommunity(LeaveCommunity leaveCommunity);

  ResponseEntity<?> rejectRequest(RejectRequest rejectRequest);

  ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityWithRooms(Long communityId);

  ResponseEntity<ApiResponse<String>> removeMemberFromCommunity(CommunityMemberRequest request);

  ResponseEntity<ApiResponse<String>> changeMemberRole(CommunityChangeRoleRequest request);

  ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityMembers(Long communityId);

  ResponseEntity<ApiResponse<String>> blockOrUnblockMember(CommunityBlockRequest request);

  ResponseEntity<ApiResponse<Community>> updateCommunityInfo(UpdateCommunityDTO dto);

  ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> listAllCommunities();

  ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityDetailsWithAdminFlag(
    Long communityId,
    String requesterEmail
  );

  ResponseEntity<?> createRoomInCommunity(CreateRoomRequest request);

  ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRoomsByCommunity(Long communityId);

  ResponseEntity<?> deleteRoom(Long roomId, String requesterEmail);

  ResponseEntity<?> searchCommunities(String q, String requesterEmail, int page, int size);

  ResponseEntity<?> enterOrRequestCommunity(Long communityId, String requesterEmail);

  ResponseEntity<?> uploadCommunityAvatar(Long communityId, String requesterEmail, MultipartFile imageFile);

  ResponseEntity<?> uploadCommunityBanner(
    Long communityId,
    String requesterEmail,
    MultipartFile bannerFile,
    MultipartFile communityAvatarFile,
    MultipartFile userAvatarFile,
    String newName,
    String newDescription
  );

  ResponseEntity<?> renameRoomInCommunity(Long communityId, Long roomId, RenameRoomRequest req);

  ResponseEntity<?> getRolesForRequester(Long communityId, String requesterEmail);

  ResponseEntity<ApiResponse<Map<String, Object>>> discoverCommunities(int page, int size);

  ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> listMyCommunities(String requesterEmail);

  ResponseEntity<ApiResponse<?>> getPendingRequests(Long communityId, String requesterEmail);

  ResponseEntity<ApiResponse<?>> getAllPendingRequestsForAdmin(String requesterEmail);
}

