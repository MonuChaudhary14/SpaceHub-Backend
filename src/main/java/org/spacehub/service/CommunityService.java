package org.spacehub.service;

import org.spacehub.DTO.*;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.User.User;
import org.spacehub.repository.CommunityRepository;
import org.spacehub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CommunityService {

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private UserRepository userRepository;

    public ResponseEntity<?> createCommunity(@RequestBody CommunityDTO community) {

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

        return ResponseEntity.ok().body(savedCommunity);

    }

    public ResponseEntity<?> deleteCommunityByName(@RequestBody DeleteCommunityDTO deleteCommunity) {

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

    public ResponseEntity<?> requestToJoinCommunity(@RequestBody JoinCommunity joinCommunity){

        if (joinCommunity.getCommunityName() == null || joinCommunity.getCommunityName().isEmpty() || joinCommunity.getUserEmail() == null || joinCommunity.getUserEmail().isEmpty()) {
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

        if(community.getMembers().contains(user)){
            return ResponseEntity.status(403).body("You are already in this community");
        }

        community.getPendingRequests().add(user);
        communityRepository.save(community);

        return ResponseEntity.ok().body("Request send to community");

    }

    public ResponseEntity<?> cancelRequestCommunity(@RequestBody CancelJoinRequest cancelJoinRequest){

        if(cancelJoinRequest.getCommunityName() == null || cancelJoinRequest.getCommunityName().isEmpty() || cancelJoinRequest.getUserEmail() == null || cancelJoinRequest.getUserEmail().isEmpty()){
            return  ResponseEntity.badRequest().body("Check the fields");
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

        if(acceptRequest.getUserEmail() == null || acceptRequest.getUserEmail().isEmpty() ||
                acceptRequest.getCommunityName() == null ||  acceptRequest.getCommunityName().isEmpty() ||
                acceptRequest.getCreatorEmail() == null || acceptRequest.getCreatorEmail().isEmpty()){

                return ResponseEntity.badRequest().body("Check the fields");
        }

        Community community = communityRepository.findByName(acceptRequest.getCommunityName());

        if (community == null)
            return ResponseEntity.badRequest().body("Community not found");

        Optional<User> optionalCreator = userRepository.findByEmail(acceptRequest.getUserEmail());

        if(optionalCreator.isEmpty()){
            return ResponseEntity.badRequest().body("creator not found");
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
        community.getMembers().add(user);

        communityRepository.save(community);

        return ResponseEntity.ok("User has been added to the community successfully");

    }

    public ResponseEntity<?> leaveCommunity(LeaveCommunity leaveCommunity){

        if(leaveCommunity.getCommunityName() == null || leaveCommunity.getCommunityName().isEmpty() || leaveCommunity.getUserEmail() == null || leaveCommunity.getUserEmail().isEmpty()){
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

        if (!community.getMembers().contains(user)) {
            return ResponseEntity.badRequest().body("You are not a member of this community");
        }

        community.getMembers().remove(user);
        communityRepository.save(community);

        return ResponseEntity.ok().body("You have left the community successfully");

    }

    public ResponseEntity<?> rejectRequest(RejectRequest rejectRequest){

        if(rejectRequest.getCommunityName() == null || rejectRequest.getCommunityName().isBlank() || rejectRequest.getUserEmail() == null || rejectRequest.getUserEmail().isBlank() || rejectRequest.getCreatorEmail().isBlank() || rejectRequest.getCreatorEmail() ==  null){
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

}
