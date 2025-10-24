package org.spacehub.service.community;

import org.spacehub.service.S3Service;
import org.springframework.transaction.annotation.Transactional;
import org.spacehub.DTO.*;
import org.spacehub.DTO.Community.*;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.Community.CommunityUser;
import org.spacehub.entities.Community.Role;
import org.spacehub.entities.User.User;
import org.spacehub.repository.ChatRoom.ChatRoomRepository;
import org.spacehub.repository.commnunity.CommunityRepository;
import org.spacehub.repository.UserRepository;
import org.spacehub.repository.commnunity.CommunityUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CommunityService {

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private CommunityUserRepository communityUserRepository;

    @Autowired
    private S3Service s3Service;

    @Transactional
    public ResponseEntity<ApiResponse<Community>> createCommunity(CommunityDTO community) {

        if (community.getName() == null || community.getName().trim().isEmpty() || community.getDescription() == null || community.getDescription().trim().isEmpty() || community.getCreatedByEmail() == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community name, description, and creator email are required", null));
        }

        if (communityRepository.findByName(community.getName()) != null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community name already exists", null));
        }

        Optional<User> optionalCreator = userRepository.findByEmail(community.getCreatedByEmail());
        if (optionalCreator.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Creator not found", null));
        }

        User creator = optionalCreator.get();

        Community infoCommunity = new Community();
        infoCommunity.setName(community.getName());
        infoCommunity.setDescription(community.getDescription());
        infoCommunity.setCreatedBy(creator);
        infoCommunity.setCreatedAt(LocalDateTime.now());

        Community savedCommunity = communityRepository.save(infoCommunity);

        CommunityUser communityUser = new CommunityUser();
        communityUser.setCommunity(savedCommunity);
        communityUser.setUser(creator);
        communityUser.setRole(Role.ADMIN);
        communityUser.setJoinDate(LocalDateTime.now());
        communityUser.setBanned(false);

        communityUserRepository.save(communityUser);

        return ResponseEntity.ok(new ApiResponse<>(200, "Community created successfully", savedCommunity));

    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> deleteCommunityByName(DeleteCommunityDTO deleteCommunity) {

        if (deleteCommunity.getName() == null || deleteCommunity.getName().trim().isEmpty() || deleteCommunity.getUserEmail() == null || deleteCommunity.getUserEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community name and user email are required", null));
        }

        Community community = communityRepository.findByName(deleteCommunity.getName().trim());
        if (community == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));
        }

        Optional<User> optionalUser = userRepository.findByEmail(deleteCommunity.getUserEmail().trim());
        if (optionalUser.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));
        }

        User user = optionalUser.get();

        if (!community.getCreatedBy().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(new ApiResponse<>(403, "You are not authorized to delete this community", null));
        }

        List<ChatRoom> chatRooms = chatRoomRepository.findByCommunityId(community.getId());
        if (!chatRooms.isEmpty()) {
            chatRoomRepository.deleteAll(chatRooms);
        }

        List<CommunityUser> members = communityUserRepository.findByCommunityId(community.getId());
        if (!members.isEmpty()) {
            communityUserRepository.deleteAll(members);
        }

        communityRepository.delete(community);
        return ResponseEntity.ok(new ApiResponse<>(200, "Community deleted successfully", null));
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> requestToJoinCommunity(JoinCommunity joinCommunity){

        if (joinCommunity.getCommunityName() == null || joinCommunity.getCommunityName().trim().isEmpty() ||
                joinCommunity.getUserEmail() == null || joinCommunity.getUserEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community name and user email are required", null));
        }

        Community community = communityRepository.findByName(joinCommunity.getCommunityName().trim());
        if (community == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));
        }

        Optional<User> optionalUser = userRepository.findByEmail(joinCommunity.getUserEmail().trim());
        if (optionalUser.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));
        }

        User user = optionalUser.get();

        boolean isMember = communityUserRepository.findByCommunityAndUser(community, user).isPresent();
        if (isMember) {
            return ResponseEntity.status(403).body(new ApiResponse<>(403, "You are already a member of this community", null));
        }

        boolean alreadyRequested = community.getPendingRequests().stream()
                .anyMatch(pendingUser -> pendingUser.getId().equals(user.getId()));

        if (alreadyRequested) {
            return ResponseEntity.status(409).body(
                    new ApiResponse<>(409, "You have already requested to join this community", null));
        }

        Optional<CommunityUser> bannedCheck = communityUserRepository.findByCommunityAndUser(community, user);
        if (bannedCheck.isPresent() && bannedCheck.get().isBanned()) {
            return ResponseEntity.status(403).body(new ApiResponse<>(403, "You are banned from this community", null));
        }

        community.getPendingRequests().add(user);
        communityRepository.save(community);

        return ResponseEntity.ok(new ApiResponse<>(200, "Request sent to community", null));
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> cancelRequestCommunity(CancelJoinRequest cancelJoinRequest){

        if (cancelJoinRequest.getCommunityName() == null || cancelJoinRequest.getCommunityName().trim().isEmpty() ||
                cancelJoinRequest.getUserEmail() == null || cancelJoinRequest.getUserEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community name and user email are required", null));
        }

        Community community = communityRepository.findByName(cancelJoinRequest.getCommunityName().trim());
        if (community == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));
        }

        Optional<User> optionalUser = userRepository.findByEmail(cancelJoinRequest.getUserEmail().trim());
        if (optionalUser.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));
        }

        User user = optionalUser.get();
        boolean hasRequested = community.getPendingRequests().stream()
                .anyMatch(pendingUser -> pendingUser.getId().equals(user.getId()));

        if (!hasRequested) {
            return ResponseEntity.status(400).body(
                    new ApiResponse<>(400, "You do not have any pending join request for this community", null)
            );
        }

        community.getPendingRequests().remove(user);
        communityRepository.save(community);

        return ResponseEntity.ok(new ApiResponse<>(200, "Cancelled the request to join the community", null));

    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> acceptRequest(AcceptRequest acceptRequest){

        if (acceptRequest.getUserEmail() == null || acceptRequest.getUserEmail().trim().isEmpty() ||
                acceptRequest.getCommunityName() == null || acceptRequest.getCommunityName().trim().isEmpty() ||
                acceptRequest.getCreatorEmail() == null || acceptRequest.getCreatorEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields", null));
        }

        Community community = communityRepository.findByName(acceptRequest.getCommunityName());

        if (community == null)
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));

        Optional<User> optionalUser = userRepository.findByEmail(acceptRequest.getUserEmail().trim());
        if (optionalUser.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));
        }

        User user = optionalUser.get();

        boolean hasPendingRequest = community.getPendingRequests()
                .stream()
                .anyMatch(pendingUser -> pendingUser.getId().equals(user.getId()));
        if (!hasPendingRequest) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "No pending request from this user", null));
        }

        boolean isMember = community.getCommunityUsers().stream()
                .anyMatch(communityUser -> communityUser.getUser().getId().equals(user.getId()));
        if (isMember) {
            community.getPendingRequests().removeIf(pendingUser -> pendingUser.getId().equals(user.getId()));
            communityRepository.save(community);
            return ResponseEntity.status(409).body(new ApiResponse<>(409, "User is already a member", null));
        }

        community.getPendingRequests().removeIf(pendingUser -> pendingUser.getId().equals(user.getId()));

        CommunityUser communityUser = new CommunityUser();
        communityUser.setCommunity(community);
        communityUser.setUser(user);
        communityUser.setRole(Role.MEMBER);
        communityUser.setJoinDate(LocalDateTime.now());
        communityUser.setBanned(false);
        communityUserRepository.save(communityUser);

        return ResponseEntity.ok(new ApiResponse<>(200, "User has been added to the community successfully", null));

    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> leaveCommunity(LeaveCommunity leaveCommunity){

        if (leaveCommunity.getCommunityName() == null || leaveCommunity.getCommunityName().trim().isEmpty() ||
                leaveCommunity.getUserEmail() == null || leaveCommunity.getUserEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community name and user email are required", null));
        }

        Community community = communityRepository.findByName(leaveCommunity.getCommunityName());
        if(community == null){
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));
        }

        Optional<User> optionalUser = userRepository.findByEmail(leaveCommunity.getUserEmail());
        if(optionalUser.isEmpty()){
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));
        }

        User user = optionalUser.get();

        if (community.getCreatedBy().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(new ApiResponse<>(403, "Community creator cannot leave their own community", null));
        }

        Optional<CommunityUser> optionalCommunityUser = communityUserRepository.findByCommunityAndUser(community, user);

        if (optionalCommunityUser.isEmpty())return ResponseEntity.badRequest().body(new ApiResponse<>(400, "You are not a member of this community", null));
        communityUserRepository.delete(optionalCommunityUser.get());

        return ResponseEntity.ok(new ApiResponse<>(200, "You have left the community successfully", null));

    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> rejectRequest(RejectRequest rejectRequest){

        if (rejectRequest.getCommunityName() == null || rejectRequest.getCommunityName().trim().isEmpty() ||
                rejectRequest.getUserEmail() == null || rejectRequest.getUserEmail().trim().isEmpty() ||
                rejectRequest.getCreatorEmail() == null || rejectRequest.getCreatorEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields", null));
        }

        Community community = communityRepository.findByName(rejectRequest.getCommunityName());
        if (community == null) return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));


        Optional<User> optionalCreator = userRepository.findByEmail(rejectRequest.getCreatorEmail());

        if (optionalCreator.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Creator not found", null));
        }

        if (!community.getCreatedBy().getId().equals(optionalCreator.get().getId())) {
            return ResponseEntity.status(403).body(new ApiResponse<>(403, "You are not authorized to reject requests", null));
        }

        Optional<User> optionalUser = userRepository.findByEmail(rejectRequest.getUserEmail());

        if (optionalUser.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));
        }

        User user = optionalUser.get();

        if (!community.getPendingRequests().contains(user)) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "No pending request from this user", null));
        }

        community.getPendingRequests().remove(user);
        communityRepository.save(community);

        return ResponseEntity.ok(new ApiResponse<>(200, "Join request rejected successfully", null));

    }

    @Transactional
    public ResponseEntity<ApiResponse<Map<String,Object>>> getCommunityWithRooms(Long communityId) {

        if(communityId == null){
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community ID is required", null));
        }

        Optional<Community> optionalCommunity = communityRepository.findById(communityId);
        if (optionalCommunity.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));
        }

        Community community = optionalCommunity.get();

        List<ChatRoom> rooms = chatRoomRepository.findByCommunityId(communityId);

        Map<String, Object> response = new HashMap<>();
        response.put("communityId", community.getId());
        response.put("communityName", community.getName());
        response.put("description", community.getDescription());
        response.put("rooms", rooms);

        List<Map<String, Object>> members = new ArrayList<>();
        for (CommunityUser communityUser : community.getCommunityUsers()) {
            Map<String, Object> memberData = new HashMap<>();
            memberData.put("email", communityUser.getUser().getEmail());
            memberData.put("role", communityUser.getRole());
            members.add(memberData);
        }
        response.put("members", members);

        return ResponseEntity.ok(new ApiResponse<>(200, "Community details fetched successfully", response));
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> removeMemberFromCommunity(CommunityMemberRequest request) {

        if (request.getCommunityId() == null || request.getUserEmail() == null || request.getRequesterEmail() == null ||
                request.getUserEmail().trim().isEmpty() || request.getRequesterEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields", null));
        }

        Optional<Community> optionalCommunity = communityRepository.findById(request.getCommunityId());
        if(optionalCommunity.isEmpty()) return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));

        Community community = optionalCommunity.get();

        Optional<User> optionalRequester = userRepository.findByEmail(request.getRequesterEmail());
        Optional<User> optionalTarget = userRepository.findByEmail(request.getUserEmail());
        if (optionalRequester.isEmpty() || optionalTarget.isEmpty()) return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));

        User requester = optionalRequester.get();
        User target = optionalTarget.get();

        if (!community.getCreatedBy().getId().equals(requester.getId())) {
            return ResponseEntity.status(403).body(new ApiResponse<>(403, "Only the community creator can remove members", null));
        }

        Optional<CommunityUser> communityUserOptional = communityUserRepository.findByCommunityAndUser(community, target);
        if(communityUserOptional.isEmpty()) return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User is not a member", null));

        communityUserRepository.delete(communityUserOptional.get());

        return ResponseEntity.ok(new ApiResponse<>(200, "Member removed successfully", null));
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> changeMemberRole(CommunityChangeRoleRequest request) {
        if (request.getCommunityId() == null ||
                request.getTargetUserEmail() == null || request.getTargetUserEmail().trim().isEmpty() ||
                request.getRequesterEmail() == null || request.getRequesterEmail().trim().isEmpty() ||
                request.getNewRole() == null || request.getNewRole().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields", null));
        }

        Optional<Community> optionalCommunity = communityRepository.findById(request.getCommunityId());
        if (optionalCommunity.isEmpty()) return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));

        Community community = optionalCommunity.get();

        Optional<User> optionalRequester = userRepository.findByEmail(request.getRequesterEmail());
        Optional<User> optionalTarget = userRepository.findByEmail(request.getTargetUserEmail());

        if (optionalRequester.isEmpty() || optionalTarget.isEmpty())
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));

        User requester = optionalRequester.get();
        User target = optionalTarget.get();

        if (!community.getCreatedBy().getId().equals(requester.getId())) {
            return ResponseEntity.status(403).body(new ApiResponse<>(403, "Only the community creator can change roles", null));
        }

        Optional<CommunityUser> optionalCommunityUser = communityUserRepository.findByCommunityAndUser(community, target);
        if (optionalCommunityUser.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User is not a member", null));
        }

        CommunityUser communityUser = optionalCommunityUser.get();

        Role newRole;
        try {
            newRole = Role.valueOf(request.getNewRole().trim().toUpperCase());
        }
        catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Invalid role: " + request.getNewRole(), null));
        }

        communityUser.setRole(newRole);
        communityUserRepository.save(communityUser);

        return ResponseEntity.ok(new ApiResponse<>(200, "Role of " + target.getEmail() + " changed to " + newRole, null));
    }

    public ResponseEntity<ApiResponse<Map<String,Object>>> getCommunityMembers(Long communityId) {

        if (communityId == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community ID is required", null));
        }

        Optional<Community> optionalCommunity = communityRepository.findById(communityId);
        if (optionalCommunity.isEmpty()) return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));

        Community community = optionalCommunity.get();

        List<CommunityUser> communityUsers = communityUserRepository.findByCommunityId(communityId);

        List<CommunityMemberDTO> members = communityUsers.stream().map(communityUser -> {
            User user = communityUser.getUser();
            return CommunityMemberDTO.builder()
                    .memberId(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .role(communityUser.getRole())
                    .joinDate(communityUser.getJoinDate())
                    .isBanned(communityUser.isBanned())
                    .build();
        }).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("communityId", community.getId());
        response.put("communityName", community.getName());
        response.put("totalMembers", members.size());
        response.put("members", members);

        return ResponseEntity.ok(new ApiResponse<>(200, "Community members fetched successfully", response));
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> blockOrUnblockMember(CommunityBlockRequest request) {

        if (request.getCommunityId() == null ||
                request.getRequesterEmail() == null || request.getRequesterEmail().trim().isEmpty() ||
                request.getTargetUserEmail() == null || request.getTargetUserEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields", null));
        }

        Optional<Community> optionalCommunity = communityRepository.findById(request.getCommunityId());
        if(optionalCommunity.isEmpty()) return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));

        Community community = optionalCommunity.get();

        Optional<User> optionalRequester = userRepository.findByEmail(request.getRequesterEmail().trim());
        Optional<User> optionalTarget = userRepository.findByEmail(request.getTargetUserEmail().trim());

        if (optionalRequester.isEmpty() || optionalTarget.isEmpty())
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));

        User requester = optionalRequester.get();
        User target = optionalTarget.get();

        if (!community.getCreatedBy().getId().equals(requester.getId())) {
            return ResponseEntity.status(403).body(new ApiResponse<>(403, "Only community creator can block or unblock members", null));
        }

        Optional<CommunityUser> optionalCommunityUser = communityUserRepository.findByCommunityAndUser(community, target);
        if(optionalCommunityUser.isEmpty()) return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User is not a member of this community", null));

        CommunityUser communityUser = optionalCommunityUser.get();
        communityUser.setBanned(request.isBlock());
        communityUserRepository.save(communityUser);

        String blocked = request.isBlock() ? "blocked" : "unblocked";
        return ResponseEntity.ok(new ApiResponse<>(200, "User " + target.getEmail() + " has been " + blocked + " successfully", null));
    }

    @Transactional
    public ResponseEntity<ApiResponse<Community>> updateCommunityInfo(UpdateCommunityDTO dto) {

        if (dto.getCommunityId() == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community ID is required", null));
        }

        Optional<Community> optionalCommunity = communityRepository.findById(dto.getCommunityId());
        if (optionalCommunity.isEmpty())
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));

        Community community = optionalCommunity.get();

        if (dto.getName() != null && !dto.getName().trim().isEmpty())
            community.setName(dto.getName().trim());
        if (dto.getDescription() != null && !dto.getDescription().trim().isEmpty())
            community.setDescription(dto.getDescription().trim());

        communityRepository.save(community);

        return ResponseEntity.ok(new ApiResponse<>(200, "Community info updated successfully", community));
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> uploadCommunityImage(Long communityId, MultipartFile file) {

        if (communityId == null || file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community ID and file are required", null));
        }

        Optional<Community> optionalCommunity = communityRepository.findById(communityId);
        if (optionalCommunity.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));
        }

        Community community = optionalCommunity.get();
        String fileName = file.getOriginalFilename();

        try {
            String imageUrl = s3Service.uploadCommunityImage(communityId, fileName, file.getInputStream(), file.getSize());

            community.setImageUrl(imageUrl);
            communityRepository.save(community);

            return ResponseEntity.ok(new ApiResponse<>(200, "Community image uploaded successfully", imageUrl));
        }
        catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse<>(500, "Failed to upload image: " + e.getMessage(), null));
        }


    }

}
