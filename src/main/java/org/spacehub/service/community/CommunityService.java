package org.spacehub.service.community;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.Community.AcceptRequest;
import org.spacehub.DTO.Community.CancelJoinRequest;
import org.spacehub.DTO.Community.CommunityBlockRequest;
import org.spacehub.DTO.Community.CommunityChangeRoleRequest;
import org.spacehub.DTO.Community.CommunityMemberRequest;
import org.spacehub.DTO.Community.CreateRoomRequest;
import org.spacehub.DTO.Community.DeleteCommunityDTO;
import org.spacehub.DTO.Community.JoinCommunity;
import org.spacehub.DTO.Community.LeaveCommunity;
import org.spacehub.DTO.Community.RejectRequest;
import org.spacehub.DTO.Community.RenameRoomRequest;
import org.spacehub.DTO.Community.UpdateCommunityDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.Community.Community;
import org.spacehub.service.community.CommunityInterfaces.ICommunityService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class CommunityService implements ICommunityService {

  private final CommunityCoreService communityCoreService;
  private final CommunityMembershipService communityMembershipService;
  private final CommunityMemberRoleService communityMemberRoleService;
  private final CommunityRoomService communityRoomService;
  private final CommunityMediaService communityMediaService;

  @Override
  public ResponseEntity<ApiResponse<Map<String, Object>>> createCommunity(
    String name, String description, MultipartFile imageFile) {
    return communityCoreService.createCommunity(name, description, imageFile);
  }

  @Override
  public ResponseEntity<?> deleteCommunityByName(DeleteCommunityDTO deleteCommunity) {
    return communityCoreService.deleteCommunityByName(deleteCommunity);
  }

  @Override
  public ResponseEntity<?> requestToJoinCommunity(JoinCommunity joinCommunity) {
    return communityMembershipService.requestToJoinCommunity(joinCommunity);
  }

  @Override
  public ResponseEntity<?> cancelRequestCommunity(CancelJoinRequest cancelJoinRequest) {
    return communityMembershipService.cancelRequestCommunity(cancelJoinRequest);
  }

  @Override
  public ResponseEntity<?> acceptRequest(AcceptRequest acceptRequest) {
    return communityMembershipService.acceptRequest(acceptRequest);
  }

  @Override
  public ResponseEntity<?> leaveCommunity(LeaveCommunity leaveCommunity) {
    return communityMembershipService.leaveCommunity(leaveCommunity);
  }

  @Override
  public ResponseEntity<?> rejectRequest(RejectRequest rejectRequest) {
    return communityMembershipService.rejectRequest(rejectRequest);
  }

  @Override
  public ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityWithRooms(UUID communityId) {
    return communityRoomService.getCommunityWithRooms(communityId);
  }

  @Override
  public ResponseEntity<ApiResponse<String>> removeMemberFromCommunity(CommunityMemberRequest request) {
    return communityMemberRoleService.removeMemberFromCommunity(request);
  }

  @Override
  public ResponseEntity<ApiResponse<String>> changeMemberRole(CommunityChangeRoleRequest request) {
    return communityMemberRoleService.changeMemberRole(request);
  }

  @Override
  public ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityMembers(UUID communityId) {
    return communityMemberRoleService.getCommunityMembers(communityId);
  }

  @Override
  public ResponseEntity<ApiResponse<String>> blockOrUnblockMember(CommunityBlockRequest request) {
    return communityMemberRoleService.blockOrUnblockMember(request);
  }

  @Override
  public ResponseEntity<ApiResponse<Community>> updateCommunityInfo(UpdateCommunityDTO dto) {
    return communityCoreService.updateCommunityInfo(dto);
  }

  @Override
  public ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> listAllCommunities() {
    return communityCoreService.listAllCommunities();
  }

  @Override
  public ResponseEntity<ApiResponse<Map<String, Object>>> getCommunityDetailsWithAdminFlag(UUID communityId) {
    return communityCoreService.getCommunityDetailsWithAdminFlag(communityId);
  }

  @Override
  public ResponseEntity<?> createRoomInCommunity(CreateRoomRequest request) {
    return communityRoomService.createRoomInCommunity(request);
  }

  @Override
  public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRoomsByCommunity(UUID communityId) {
    return communityRoomService.getRoomsByCommunity(communityId);
  }

  @Override
  public ResponseEntity<?> deleteRoom(UUID communityId, UUID roomId) {
    return communityRoomService.deleteRoom(communityId, roomId);
  }

  @Override
  public ResponseEntity<?> searchCommunities(String q, int page, int size) {
    return communityCoreService.searchCommunities(q, page, size);
  }

  @Override
  public ResponseEntity<?> enterOrRequestCommunity(UUID communityId) {
    return communityMembershipService.enterOrRequestCommunity(communityId);
  }

  @Override
  public ResponseEntity<?> uploadCommunityAvatar(UUID communityId, MultipartFile imageFile) {
    return communityMediaService.uploadCommunityAvatar(communityId, imageFile);
  }

  @Override
  public ResponseEntity<?> uploadCommunityBanner(
    UUID communityId,
    MultipartFile bannerFile,
    MultipartFile communityAvatarFile,
    MultipartFile userAvatarFile,
    String newName,
    String newDescription
  ) {
    return communityMediaService.uploadCommunityBanner(
      communityId, bannerFile, communityAvatarFile, userAvatarFile, newName, newDescription
    );
  }

  @Override
  public ResponseEntity<?> renameRoomInCommunity(UUID communityId, UUID roomId, RenameRoomRequest req) {
    return communityRoomService.renameRoomInCommunity(communityId, roomId, req);
  }

  @Override
  public ResponseEntity<?> getRolesForRequester(UUID communityId) {
    return communityMemberRoleService.getRolesForRequester(communityId);
  }

  @Override
  public ResponseEntity<ApiResponse<Map<String, Object>>> discoverCommunities(int page, int size) {
    return communityCoreService.discoverCommunities(page, size);
  }

  @Override
  public ResponseEntity<ApiResponse<Map<String, List<Map<String, Object>>>>> listMyCommunities() {
    return communityCoreService.listMyCommunities();
  }

  @Override
  public ResponseEntity<ApiResponse<?>> getPendingRequests(UUID communityId) {
    return communityMembershipService.getPendingRequests(communityId);
  }

  @Override
  public ResponseEntity<ApiResponse<?>> getAllPendingRequestsForAdmin() {
    return communityMembershipService.getAllPendingRequestsForAdmin();
  }

  @Override
  public ResponseEntity<?> checkCommunityNameExists(String name) {
    return communityCoreService.checkCommunityNameExists(name);
  }
}
