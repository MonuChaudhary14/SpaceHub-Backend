package org.spacehub.service.community;

import org.spacehub.DTO.*;
import org.spacehub.DTO.Community.*;
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

    public ResponseEntity<?> createCommunity(CommunityDTO community) {

        if(community.getName() == null || community.getDescription() == null){
            return ResponseEntity.badRequest().body("Check the fields");
        }

        Optional<User> userOptional = userRepository.findByEmail(community.getCreatedByEmail());

        if(userOptional.isEmpty()){
            return ResponseEntity.badRequest().body("Not created");
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

        return ResponseEntity.ok().body(savedCommunity);

    }

    public ResponseEntity<?> deleteCommunityByName(DeleteCommunityDTO deleteCommunity) {

        String name = deleteCommunity.getName();
        String userEmail = deleteCommunity.getUserEmail();

        Community community = communityRepository.findByName(name);
        if (community == null) {
            return ResponseEntity.badRequest().body("Community not found");
        }

        Optional<User> userOptional = userRepository.findByEmail(userEmail);
        if (userOptional.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }

        User user = userOptional.get();

        if (!community.getCreatedBy().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body("You are not authorized to delete this community");
        }

        communityRepository.delete(community);
        return ResponseEntity.ok().body("Community deleted successfully");
    }

    public ResponseEntity<?> requestToJoinCommunity(JoinCommunity joinCommunity){

        if (joinCommunity.getCommunityName() == null || joinCommunity.getUserEmail() == null) {
            return ResponseEntity.badRequest().body("Check the fields");
        }

        Community community = communityRepository.findByName(joinCommunity.getCommunityName());

        if(community == null){
            return ResponseEntity.badRequest().body("Community not found");
        }

        Optional<User> optionalUser = userRepository.findByEmail(joinCommunity.getUserEmail());

        if(optionalUser.isEmpty()){
            return ResponseEntity.badRequest().body("User not found");
        }

        User user = optionalUser.get();

        boolean isMember = community.getCommunityUsers().stream().anyMatch(communityUser -> communityUser.getUser().getId().equals(user.getId()));

        if (isMember) return ResponseEntity.status(403).body("You are already in this community");

        community.getPendingRequests().add(user);
        communityRepository.save(community);

        return ResponseEntity.ok().body("Request send to community");
    }

    public ResponseEntity<?> cancelRequestCommunity(CancelJoinRequest cancelJoinRequest){

        if (cancelJoinRequest.getCommunityName() == null || cancelJoinRequest.getUserEmail() == null) {
            return ResponseEntity.badRequest().body("Check the fields");
        }

        Community community = communityRepository.findByName(cancelJoinRequest.getCommunityName());

        if(community == null){
            return  ResponseEntity.badRequest().body("Community not found");
        }

        Optional<User> optionalUser = userRepository.findByEmail(cancelJoinRequest.getUserEmail());

        if(optionalUser.isEmpty()){
            return ResponseEntity.badRequest().body("User not found");
        }

        User user = optionalUser.get();

        if(!community.getPendingRequests().contains(user)){
            return ResponseEntity.status(403).body("No request found for this community");
        }

        community.getPendingRequests().remove(user);
        communityRepository.save(community);

        return ResponseEntity.ok().body("Cancelled the request to join the community");

    }

    public ResponseEntity<?> acceptRequest(AcceptRequest acceptRequest){

        if (acceptRequest.getUserEmail() == null || acceptRequest.getCommunityName() == null || acceptRequest.getCreatorEmail() == null) {
            return ResponseEntity.badRequest().body("Check the fields");
        }

        Community community = communityRepository.findByName(acceptRequest.getCommunityName());

        if (community == null)
            return ResponseEntity.badRequest().body("Community not found");

        Optional<User> optionalCreator = userRepository.findByEmail(acceptRequest.getCreatorEmail());

        if(optionalCreator.isEmpty()){
            return ResponseEntity.badRequest().body("Creator not found");
        }

        User creator = optionalCreator.get();

        if(!community.getCreatedBy().getId().equals(creator.getId())){
            return  ResponseEntity.status(403).body("You are not authorized to create this community");
        }

        Optional<User> optionalUser = userRepository.findByEmail(acceptRequest.getUserEmail());
        if (optionalUser.isEmpty()) return ResponseEntity.badRequest().body("User not found");

        User user = optionalUser.get();

        if (!community.getPendingRequests().contains(user)) {
            return ResponseEntity.badRequest().body("No pending request from this user");
        }

        community.getPendingRequests().remove(user);

        CommunityUser communityUser = new CommunityUser();
        communityUser.setCommunity(community);
        communityUser.setUser(user);
        communityUser.setRole(Role.MEMBER);
        communityUser.setJoinDate(LocalDateTime.now());
        communityUser.setBanned(false);
        communityUserRepository.save(communityUser);

        return ResponseEntity.ok("User has been added to the community successfully");

    }

    public ResponseEntity<?> leaveCommunity(LeaveCommunity leaveCommunity){

        if (leaveCommunity.getCommunityName() == null || leaveCommunity.getUserEmail() == null) {
            return ResponseEntity.badRequest().body("Check the fields");
        }

        Community community = communityRepository.findByName(leaveCommunity.getCommunityName());
        if(community == null){
            return ResponseEntity.badRequest().body("Community not found");
        }

        Optional<User> optionalUser = userRepository.findByEmail(leaveCommunity.getUserEmail());
        if(optionalUser.isEmpty()){
            return ResponseEntity.badRequest().body("User not found");
        }

        User user = optionalUser.get();

        if (community.getCreatedBy().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body("Community creator cannot leave their own community");
        }

        Optional<CommunityUser> optionalCommunityUser = community.getCommunityUsers().stream()
                .filter(communityUser -> communityUser.getUser().getId().equals(user.getId()))
                .findFirst();

        if (optionalCommunityUser.isEmpty()) return ResponseEntity.badRequest().body("You are not a member of this community");

        communityUserRepository.delete(optionalCommunityUser.get());

        return ResponseEntity.ok().body("You have left the community successfully");

    }

    public ResponseEntity<?> rejectRequest(RejectRequest rejectRequest){

        if (rejectRequest.getCommunityName() == null || rejectRequest.getUserEmail() == null || rejectRequest.getCreatorEmail() == null) {
            return ResponseEntity.badRequest().body("Check the fields");
        }

        Community community = communityRepository.findByName(rejectRequest.getCommunityName());
        if (community == null) return ResponseEntity.badRequest().body("Community not found");


        Optional<User> optionalCreator = userRepository.findByEmail(rejectRequest.getCreatorEmail());

        if (optionalCreator.isEmpty()) {
            return ResponseEntity.badRequest().body("Creator not found");
        }

        if (!community.getCreatedBy().getId().equals(optionalCreator.get().getId())) {
            return ResponseEntity.status(403).body("You are not authorized to reject requests");
        }

        Optional<User> optionalUser = userRepository.findByEmail(rejectRequest.getUserEmail());

        if (optionalUser.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }

        User user = optionalUser.get();

        if (!community.getPendingRequests().contains(user)) {
            return ResponseEntity.badRequest().body("No pending request from this user");
        }

        community.getPendingRequests().remove(user);
        communityRepository.save(community);

        return ResponseEntity.ok().body("Join request rejected successfully");

    }

    public ResponseEntity<?> getCommunityWithRooms(Long communityId) {

        Optional<Community> optionalCommunity = communityRepository.findById(communityId);
        if (optionalCommunity.isEmpty()) {
            return ResponseEntity.badRequest().body("Community not found");
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

        return ResponseEntity.ok(response);
    }

    public ResponseEntity<?> removeMemberFromCommunity(CommunityMemberRequest request) {

        if (request.getCommunityId() == null || request.getUserEmail() == null || request.getRequesterEmail() == null) {
            return ResponseEntity.badRequest().body("Check the fields");
        }

        Community community = communityRepository.findById(request.getCommunityId()).orElse(null);
        if (community == null) return ResponseEntity.badRequest().body("Community not found");

        Optional<User> optionalRequester = userRepository.findByEmail(request.getRequesterEmail());
        Optional<User> optionalTarget = userRepository.findByEmail(request.getUserEmail());
        if (optionalRequester.isEmpty() || optionalTarget.isEmpty()) return ResponseEntity.badRequest().body("User not found");

        User requester = optionalRequester.get();
        User target = optionalTarget.get();

        if (!community.getCreatedBy().getId().equals(requester.getId())) {
            return ResponseEntity.status(403).body("Only the community creator can remove members");
        }

        Optional<CommunityUser> communityUserOptional = community.getCommunityUsers()
                .stream().filter(communityUser -> communityUser.getUser().getId().equals(target.getId()))
                .findFirst();

        if (communityUserOptional.isEmpty()) {
            return ResponseEntity.badRequest().body("User is not a member");
        }

        community.getCommunityUsers().remove(communityUserOptional.get());
        communityRepository.save(community);

        return ResponseEntity.ok("Member removed successfully");
    }

    public ResponseEntity<?> changeMemberRole(CommunityChangeRoleRequest request) {
        if (request.getCommunityId() == null || request.getTargetUserEmail() == null ||
                request.getRequesterEmail() == null || request.getNewRole() == null) {
            return ResponseEntity.badRequest().body("Check the fields");
        }

        Community community = communityRepository.findById(request.getCommunityId()).orElse(null);
        if (community == null) return ResponseEntity.badRequest().body("Community not found");

        Optional<User> optionalRequester = userRepository.findByEmail(request.getRequesterEmail());
        Optional<User> optionalTarget = userRepository.findByEmail(request.getTargetUserEmail());

        if (optionalRequester.isEmpty() || optionalTarget.isEmpty())
            return ResponseEntity.badRequest().body("User not found");

        User requester = optionalRequester.get();
        User target = optionalTarget.get();

        if (!community.getCreatedBy().getId().equals(requester.getId())) {
            return ResponseEntity.status(403).body("Only the community creator can change roles");
        }

        Optional<CommunityUser> communityUserOptional = community.getCommunityUsers()
                .stream()
                .filter(communityUser -> communityUser.getUser().getId().equals(target.getId()))
                .findFirst();

        if (communityUserOptional.isEmpty()) {
            return ResponseEntity.badRequest().body("User is not a member");
        }

        CommunityUser communityUser = communityUserOptional.get();

        Role newRole;
        try {
            newRole = Role.valueOf(request.getNewRole().toUpperCase());
        }
        catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid role: " + request.getNewRole());
        }

        communityUser.setRole(newRole);
        communityUserRepository.save(communityUser);

        return ResponseEntity.ok("Role of " + target.getEmail() + " changed to " + newRole);
    }

    public ResponseEntity<?> getCommunityMembers(Long communityId) {
        Optional<Community> optionalCommunity = communityRepository.findById(communityId);
        if (optionalCommunity.isEmpty()) {
            return ResponseEntity.badRequest().body("Community not found");
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

        return ResponseEntity.ok(response);
    }

}
