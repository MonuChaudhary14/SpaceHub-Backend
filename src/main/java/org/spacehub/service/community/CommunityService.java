package org.spacehub.service.community;

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

    public ResponseEntity<ApiResponse<Community>> createCommunity(CommunityDTO community) {

        if(community.getName() == null || community.getDescription() == null){
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields", null));
        }

        Optional<User> userOptional = userRepository.findByEmail(community.getCreatedByEmail());

        if(userOptional.isEmpty()){
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User does not exist", null));
        }

        User creator = userOptional.get();

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

    public ResponseEntity<ApiResponse<String>> deleteCommunityByName(DeleteCommunityDTO deleteCommunity) {

        String name = deleteCommunity.getName();
        String userEmail = deleteCommunity.getUserEmail();

        Community community = communityRepository.findByName(name);
        if (community == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));
        }

        Optional<User> userOptional = userRepository.findByEmail(userEmail);
        if (userOptional.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));
        }

        User user = userOptional.get();

        if (!community.getCreatedBy().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(new ApiResponse<>(403, "You are not authorized to delete this community", null));
        }

        communityRepository.delete(community);
        return ResponseEntity.ok(new ApiResponse<>(200, "Community deleted successfully", null));
    }

    public ResponseEntity<ApiResponse<String>> requestToJoinCommunity(JoinCommunity joinCommunity){

        if (joinCommunity.getCommunityName() == null || joinCommunity.getUserEmail() == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields", null));
        }

        Community community = communityRepository.findByName(joinCommunity.getCommunityName());

        if(community == null){
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));
        }

        Optional<User> optionalUser = userRepository.findByEmail(joinCommunity.getUserEmail());

        if(optionalUser.isEmpty()){
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));
        }

        User user = optionalUser.get();

        boolean isMember = community.getCommunityUsers().stream().anyMatch(communityUser -> communityUser.getUser().getId().equals(user.getId()));

        if (isMember) return ResponseEntity.status(403).body(new ApiResponse<>(403, "You are already in this community", null));

        community.getPendingRequests().add(user);
        communityRepository.save(community);

        return ResponseEntity.ok(new ApiResponse<>(200, "Request sent to community", null));
    }

    public ResponseEntity<ApiResponse<String>> cancelRequestCommunity(CancelJoinRequest cancelJoinRequest){

        if (cancelJoinRequest.getCommunityName() == null || cancelJoinRequest.getUserEmail() == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields", null));
        }

        Community community = communityRepository.findByName(cancelJoinRequest.getCommunityName());

        if(community == null){
            return  ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));
        }

        Optional<User> optionalUser = userRepository.findByEmail(cancelJoinRequest.getUserEmail());

        if(optionalUser.isEmpty()){
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));
        }

        User user = optionalUser.get();

        if(!community.getPendingRequests().contains(user)){
            return ResponseEntity.status(403).body(new ApiResponse<>(403, "No request found for this community", null));
        }

        community.getPendingRequests().remove(user);
        communityRepository.save(community);

        return ResponseEntity.ok(new ApiResponse<>(200, "Cancelled the request to join the community", null));

    }

    public ResponseEntity<ApiResponse<String>> acceptRequest(AcceptRequest acceptRequest){

        if (acceptRequest.getUserEmail() == null || acceptRequest.getCommunityName() == null || acceptRequest.getCreatorEmail() == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields", null));
        }

        Community community = communityRepository.findByName(acceptRequest.getCommunityName());

        if (community == null)
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));

        Optional<User> optionalCreator = userRepository.findByEmail(acceptRequest.getCreatorEmail());

        if(optionalCreator.isEmpty()){
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Creator not found", null));
        }

        User creator = optionalCreator.get();

        if(!community.getCreatedBy().getId().equals(creator.getId())){
            return  ResponseEntity.status(403).body(new ApiResponse<>(403, "You are not authorized to accept requests", null));
        }

        Optional<User> optionalUser = userRepository.findByEmail(acceptRequest.getUserEmail());
        if (optionalUser.isEmpty()) return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));

        User user = optionalUser.get();

        if (!community.getPendingRequests().contains(user)) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "No pending request from this user", null));
        }

        community.getPendingRequests().remove(user);

        CommunityUser communityUser = new CommunityUser();
        communityUser.setCommunity(community);
        communityUser.setUser(user);
        communityUser.setRole(Role.MEMBER);
        communityUser.setJoinDate(LocalDateTime.now());
        communityUser.setBanned(false);
        communityUserRepository.save(communityUser);

        return ResponseEntity.ok(new ApiResponse<>(200, "User has been added to the community successfully", null));

    }

    public ResponseEntity<ApiResponse<String>> leaveCommunity(LeaveCommunity leaveCommunity){

        if (leaveCommunity.getCommunityName() == null || leaveCommunity.getUserEmail() == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields", null));
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

        Optional<CommunityUser> optionalCommunityUser = community.getCommunityUsers().stream()
                .filter(communityUser -> communityUser.getUser().getId().equals(user.getId()))
                .findFirst();

        if (optionalCommunityUser.isEmpty())return ResponseEntity.badRequest().body(new ApiResponse<>(400, "You are not a member of this community", null));
        communityUserRepository.delete(optionalCommunityUser.get());

        return ResponseEntity.ok(new ApiResponse<>(200, "You have left the community successfully", null));

    }

    public ResponseEntity<ApiResponse<String>> rejectRequest(RejectRequest rejectRequest){

        if (rejectRequest.getCommunityName() == null || rejectRequest.getUserEmail() == null || rejectRequest.getCreatorEmail() == null) {
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

    public ResponseEntity<ApiResponse<Map<String,Object>>> getCommunityWithRooms(Long communityId) {

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

    public ResponseEntity<ApiResponse<String>> removeMemberFromCommunity(CommunityMemberRequest request) {

        if (request.getCommunityId() == null || request.getUserEmail() == null || request.getRequesterEmail() == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields", null));
        }

        Community community = communityRepository.findById(request.getCommunityId()).orElse(null);
        if (community == null) return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));

        Optional<User> optionalRequester = userRepository.findByEmail(request.getRequesterEmail());
        Optional<User> optionalTarget = userRepository.findByEmail(request.getUserEmail());
        if (optionalRequester.isEmpty() || optionalTarget.isEmpty()) return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));

        User requester = optionalRequester.get();
        User target = optionalTarget.get();

        if (!community.getCreatedBy().getId().equals(requester.getId())) {
            return ResponseEntity.status(403).body(new ApiResponse<>(403, "Only the community creator can remove members", null));
        }

        Optional<CommunityUser> communityUserOptional = community.getCommunityUsers()
                .stream().filter(communityUser -> communityUser.getUser().getId().equals(target.getId()))
                .findFirst();

        if (communityUserOptional.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User is not a member", null));
        }

        community.getCommunityUsers().remove(communityUserOptional.get());
        communityRepository.save(community);

        return ResponseEntity.ok(new ApiResponse<>(200, "Member removed successfully", null));
    }

    public ResponseEntity<ApiResponse<String>> changeMemberRole(CommunityChangeRoleRequest request) {
        if (request.getCommunityId() == null || request.getTargetUserEmail() == null ||
                request.getRequesterEmail() == null || request.getNewRole() == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields", null));
        }

        Community community = communityRepository.findById(request.getCommunityId()).orElse(null);
        if (community == null)return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));

        Optional<User> optionalRequester = userRepository.findByEmail(request.getRequesterEmail());
        Optional<User> optionalTarget = userRepository.findByEmail(request.getTargetUserEmail());

        if (optionalRequester.isEmpty() || optionalTarget.isEmpty())
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));

        User requester = optionalRequester.get();
        User target = optionalTarget.get();

        if (!community.getCreatedBy().getId().equals(requester.getId())) {
            return ResponseEntity.status(403).body(new ApiResponse<>(403, "Only the community creator can change roles", null));
        }

        Optional<CommunityUser> communityUserOptional = community.getCommunityUsers()
                .stream()
                .filter(communityUser -> communityUser.getUser().getId().equals(target.getId()))
                .findFirst();

        if (communityUserOptional.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User is not a member", null));
        }

        CommunityUser communityUser = communityUserOptional.get();

        Role newRole;
        try {
            newRole = Role.valueOf(request.getNewRole().toUpperCase());
        }
        catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Invalid role: " + request.getNewRole(), null));
        }

        communityUser.setRole(newRole);
        communityUserRepository.save(communityUser);

        return ResponseEntity.ok(new ApiResponse<>(200, "Role of " + target.getEmail() + " changed to " + newRole, null));
    }

    public ResponseEntity<ApiResponse<Map<String,Object>>> getCommunityMembers(Long communityId) {
        Optional<Community> optionalCommunity = communityRepository.findById(communityId);
        if (optionalCommunity.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));
        }
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

    public ResponseEntity<ApiResponse<String>> blockOrUnblockMember(CommunityBlockRequest request) {

        if(request.getCommunityId() == null || request.getRequesterEmail() == null || request.getTargetUserEmail() == null){
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Check the fields", null));
        }

        Optional<Community> optionalCommunity = communityRepository.findById(request.getCommunityId());

        if(optionalCommunity.isEmpty()) return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));

        Community community = optionalCommunity.get();

        Optional<User> optionalRequester = userRepository.findByEmail(request.getRequesterEmail());
        Optional<User> optionalTarget = userRepository.findByEmail(request.getTargetUserEmail());

        if (optionalRequester.isEmpty() || optionalTarget.isEmpty())return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));

        User requester = optionalRequester.get();
        User target = optionalTarget.get();

        if (!community.getCreatedBy().getId().equals(requester.getId())) {
            return ResponseEntity.status(403).body(new ApiResponse<>(403, "Only community creator can block or unblock members", null));
        }

        Optional<CommunityUser> optionalCommunityUser = community.getCommunityUsers().stream()
                .filter(communityUser -> communityUser.getUser().getId().equals(target.getId()))
                .findFirst();

        if (optionalCommunityUser.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User is not a member of this community", null));
        }

        CommunityUser communityUser = optionalCommunityUser.get();

        communityUser.setBanned(request.isBlock());
        communityUserRepository.save(communityUser);

        String blocked = request.isBlock() ? "blocked" : "unblocked";
        return ResponseEntity.ok(new ApiResponse<>(200, "User " + target.getEmail() + " has been " + blocked + " successfully", null));
    }

    public ResponseEntity<ApiResponse<Community>> updateCommunityInfo(UpdateCommunityDTO dto) {

        Optional<Community> optionalCommunity = communityRepository.findById(dto.getCommunityId());
        if (optionalCommunity.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "Community not found", null));
        }

        Community community = optionalCommunity.get();

        if (dto.getName() != null) community.setName(dto.getName());
        if (dto.getDescription() != null) community.setDescription(dto.getDescription());

        communityRepository.save(community);

        return ResponseEntity.ok(new ApiResponse<>(200, "Community info updated successfully", community));
    }

}
