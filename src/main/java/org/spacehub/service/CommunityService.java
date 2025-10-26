package org.spacehub.service;

import org.spacehub.DTO.*;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.User.User;
import org.spacehub.repository.CommunityRepository;
import org.spacehub.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CommunityService {

  private final CommunityRepository communityRepository;
  private final UserRepository userRepository;

  public static class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
      super(message);
    }
  }

  public CommunityService(CommunityRepository communityRepository, UserRepository userRepository) {
    this.communityRepository = communityRepository;
    this.userRepository = userRepository;
  }

  public ResponseEntity<?> createCommunity(@RequestBody CommunityDTO community) {

    if (community.getName() != null && community.getDescription() != null) {
      Optional<User> userOptional = userRepository.findByEmail(community.getCreatedByEmail());

      if (userOptional.isPresent()) {
        User creator = userOptional.get();

        Community infoCommunity = new Community();
        infoCommunity.setName(community.getName());
        infoCommunity.setDescription(community.getDescription());
        infoCommunity.setCreatedBy(creator);
        infoCommunity.setCreatedAt(LocalDateTime.now());

        Community savedCommunity = communityRepository.save(infoCommunity);

        return ResponseEntity.ok().body(savedCommunity);
      } else {
        return ResponseEntity.badRequest().body("Not created");
      }
    } else {
      return ResponseEntity.badRequest().body("Check the fields");
    }

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

    if (joinCommunity.getCommunityName() == null || joinCommunity.getCommunityName().isEmpty() ||
      joinCommunity.getUserEmail() == null || joinCommunity.getUserEmail().isEmpty()) {
      return ResponseEntity.badRequest().body("Check the fields");
    }

    Community community = communityRepository.findByName(joinCommunity.getCommunityName());

    if (community != null) {
      Optional<User> optionalUser = userRepository.findByEmail(joinCommunity.getUserEmail());

      if (optionalUser.isEmpty()) {
        return ResponseEntity.badRequest().body("User not found");
      }

      User user = optionalUser.get();

      if (community.getMembers().contains(user)) {
        return ResponseEntity.status(403).body("You are already in this community");
      }

      community.getPendingRequests().add(user);
      communityRepository.save(community);

      return ResponseEntity.ok().body("Request send to community");
    } else {
      return ResponseEntity.badRequest().body("Community not found");
    }

  }

  public ResponseEntity<?> cancelRequestCommunity(@RequestBody CancelJoinRequest cancelJoinRequest){

    if (cancelJoinRequest.getCommunityName() != null && !cancelJoinRequest.getCommunityName().isEmpty() &&
      cancelJoinRequest.getUserEmail() != null && !cancelJoinRequest.getUserEmail().isEmpty()) {
      Community community = communityRepository.findByName(cancelJoinRequest.getCommunityName());

      if (community == null) {
        return ResponseEntity.badRequest().body("Community not found");
      }

      Optional<User> optionalUser = userRepository.findByEmail(cancelJoinRequest.getUserEmail());

      if (optionalUser.isEmpty()) {
        return ResponseEntity.badRequest().body("User not found");
      }

      User user = optionalUser.get();

      if (!community.getPendingRequests().contains(user)) {
        return ResponseEntity.status(403).body("No request found for this community");
      }

      community.getPendingRequests().remove(user);
      communityRepository.save(community);

      return ResponseEntity.ok().body("Cancelled the request to join the community");
    } else {
      return ResponseEntity.badRequest().body("Check the fields");
    }

  }

  public ResponseEntity<?> acceptRequest(AcceptRequest acceptRequest) {

    if (isEmpty(acceptRequest.getUserEmail(), acceptRequest.getCommunityName(), acceptRequest.getCreatorEmail())) {
      return ResponseEntity.badRequest().body("Check the fields");
    }

    try {
      Community community = findCommunityByName(acceptRequest.getCommunityName());
      User creator = findUserByEmail(acceptRequest.getCreatorEmail());
      User user = findUserByEmail(acceptRequest.getUserEmail());

      if (!community.getCreatedBy().getId().equals(creator.getId())) {
        return ResponseEntity.status(403).body("You are not authorized to accept requests");
      }

      if (!community.getPendingRequests().contains(user)) {
        return ResponseEntity.badRequest().body("No pending request from this user");
      }

      community.getPendingRequests().remove(user);
      community.getMembers().add(user);

      communityRepository.save(community);

      return ResponseEntity.ok("User has been added to the community successfully");

    } catch (ResourceNotFoundException ex) {
      return ResponseEntity.badRequest().body(ex.getMessage());
    }
  }

  private boolean isEmpty(String... values) {
    for (String val : values) {
      if (val == null || val.isBlank()) return true;
    }
    return false;
  }

  public ResponseEntity<?> leaveCommunity(LeaveCommunity leaveCommunity) {

    if (isEmpty(leaveCommunity.getCommunityName(), leaveCommunity.getUserEmail())) {
      return ResponseEntity.badRequest().body("Check the fields");
    }

    try {
      Community community = findCommunityByName(leaveCommunity.getCommunityName());
      User user = findUserByEmail(leaveCommunity.getUserEmail());

      if (community.getCreatedBy().getId().equals(user.getId())) {
        return ResponseEntity.status(403).body("Community creator cannot leave their own community");
      }

      if (!community.getMembers().contains(user)) {
        return ResponseEntity.badRequest().body("You are not a member of this community");
      }

      community.getMembers().remove(user);
      communityRepository.save(community);

      return ResponseEntity.ok().body("You have left the community successfully");

    } catch (ResourceNotFoundException ex) {
      return ResponseEntity.badRequest().body(ex.getMessage());
    }
  }

  public ResponseEntity<?> rejectRequest(RejectRequest rejectRequest){

    if (rejectRequest.getCommunityName() != null && !rejectRequest.getCommunityName().isBlank() &&
      rejectRequest.getUserEmail() != null && !rejectRequest.getUserEmail().isBlank() && !rejectRequest.getCreatorEmail().isBlank()) {
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
    } else {
      return ResponseEntity.badRequest().body("Check the fields");
    }

  }

  private Community findCommunityByName(String name) {
    Community community = communityRepository.findByName(name);
    if (community == null) {
      throw new ResourceNotFoundException("Community not found");
    }
    return community;
  }

  private User findUserByEmail(String email) {
    return userRepository.findByEmail(email)
      .orElseThrow(() -> new ResourceNotFoundException("User not found"));
  }

}
